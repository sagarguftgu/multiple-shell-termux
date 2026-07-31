package com.multishell.termuxapp;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.*;
import java.util.HashMap;

@CapacitorPlugin(name = "RealTerminalPlugin")
public class RealTerminalPlugin extends Plugin {
    private HashMap<Integer, Process> processes = new HashMap<>();
    private HashMap<Integer, OutputStream> inputs = new HashMap<>();

    @PluginMethod
    public void startShell(PluginCall call) {
        int shellId = call.getInt("shellId", 1);
        if (processes.containsKey(shellId)) {
            call.resolve();
            return;
        }

        try {
            // 💡 यहाँ एरर फिक्स किया गया है: '-i' को हटाकर नॉर्मल 'sh' किया है ताकि स्ट्रीम ब्लॉक न हो
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh");
            pb.redirectErrorStream(true);
            
            // ऐप का अपना फोल्डर सेट करना जहाँ कमांड्स बिना किसी सुरक्षा एरर के चल सकें
            File privateDir = getContext().getFilesDir();
            pb.directory(privateDir); 
            
            // एनवायरनमेंट वेरिएबल्स सेट करना ताकि कमांड्स को रास्ता मिल सके
            pb.environment().put("PATH", "/system/bin:/system/xbin:" + privateDir.getAbsolutePath() + "/usr/bin");
            pb.environment().put("HOME", privateDir.getAbsolutePath());

            Process process = pb.start();
            processes.put(shellId, process);
            inputs.put(shellId, process.getOutputStream());

            final InputStream shellOutput = process.getInputStream();

            // आउटपुट लगातार पढ़ने के लिए थ्रेड
            new Thread(() -> {
                byte[] buffer = new byte[1024];
                int bytesRead;
                try {
                    while ((bytesRead = shellOutput.read(buffer)) != -1) {
                        String outputStr = new String(buffer, 0, bytesRead);
                        JSObject data = new JSObject();
                        data.put("shellId", shellId); 
                        data.put("output", outputStr);
                        notifyListeners("onShellOutput", data);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            
            // 💡 शेल शुरू होते ही यूजर को स्क्रीन पर एक प्रॉम्प्ट दिखाने के लिए
            JSObject initialMsg = new JSObject();
            initialMsg.put("shellId", shellId);
            initialMsg.put("output", "\r\n$ ");
            notifyListeners("onShellOutput", initialMsg);
            
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void writeToShell(PluginCall call) {
        int shellId = call.getInt("shellId", 1);
        String input = call.getString("input");
        
        try {
            OutputStream shellInput = inputs.get(shellId);
            if (shellInput != null && input != null) {
                // इनपुट को शेल में राइट करना
                shellInput.write(input.getBytes());
                shellInput.flush();
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void closeShell(PluginCall call) {
        int shellId = call.getInt("shellId", 1);
        try {
            Process p = processes.get(shellId);
            if (p != null) {
                p.destroy();
                processes.remove(shellId);
                inputs.remove(shellId);
            }
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }
}
