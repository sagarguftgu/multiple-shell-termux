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
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-i");
            pb.redirectErrorStream(true);
            pb.directory(getContext().getFilesDir()); 
            
            Process process = pb.start();
            processes.put(shellId, process);
            inputs.put(shellId, process.getOutputStream());

            final InputStream shellOutput = process.getInputStream();

            new Thread(() -> {
                byte[] buffer = new byte;
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
                String trimmedInput = input.trim();
                
                // टर्मक्स का लाइव पैकेज मैनेजर इंटीग्रेशन (.deb सर्वर सपोर्ट)
                if (trimmedInput.startsWith("pkg install ") || trimmedInput.startsWith("apt install ")) {
                    String packageName = trimmedInput.replace("pkg install ", "").replace("apt install ", "").trim();
                    
                    JSObject msg = new JSObject();
                    msg.put("shellId", shellId);
                    msg.put("output", "\r\n[Apt Manager]: Connecting to official Termux mirrors...\r\n");
                    notifyListeners("onShellOutput", msg);

                    String termuxMirror = "https://termux.org";
                    
                    String aptScript = "echo '[Apt Manager]: Fetching details for " + packageName + "...' && " +
                                       "DEB_PATH=$(curl -s " + termuxMirror + " | grep -A 10 'Package: " + packageName + "$' | grep 'Filename:' | awk '{print $2}') && " +
                                       "if [ -z \"$DEB_PATH\" ]; then " +
                                       "  echo '\nError: Package \"" + packageName + "\" not found.\n'; " +
                                       "else " +
                                       "  FULL_URL=\"https://termux.org\"$DEB_PATH && " +
                                       "  curl -LO $FULL_URL && " +
                                       "  FILENAME=$(basename $DEB_PATH) && " +
                                       "  ar x $FILENAME && tar -xvf data.tar.xz && " +
                                       "  rm $FILENAME control.tar.xz data.tar.xz debian-binary 2>/dev/null && " +
                                       "  export PATH=$PATH:$(pwd)/usr/bin && " +
                                       "  echo '\n🎉 " + packageName + " installed successfully!\n'; " +
                                       "fi\n";
                    
                    shellInput.write(aptScript.getBytes());
                } else {
                    shellInput.write(input.getBytes());
                }
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
