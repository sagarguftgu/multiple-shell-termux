import React, { useEffect, useRef, useState } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';
import { registerPlugin } from '@capacitor/core';

const RealTerminalPlugin = registerPlugin('RealTerminalPlugin');

export default function TerminalComponent() {
  const [tabs, setTabs] = useState([{ id: 1, name: 'Shell 1' }]);
  const [activeTab, setActiveTab] = useState(1);
  const terminalContainerRef = useRef(null);
  const terminalsRef = useRef({});

  const addNewTab = () => {
    const newId = tabs.length > 0 ? tabs[tabs.length - 1].id + 1 : 1;
    setTabs([...tabs, { id: newId, name: `Shell ${newId}` }]);
    setActiveTab(newId);
  };

  const closeTab = (idToClose, e) => {
    e.stopPropagation();
    const updatedTabs = tabs.filter(tab => tab.id !== idToClose);
    setTabs(updatedTabs);
    
    RealTerminalPlugin.closeShell({ shellId: idToClose });
    if (terminalsRef.current[idToClose]) {
      terminalsRef.current[idToClose].dispose();
      delete terminalsRef.current[idToClose];
    }

    if (activeTab === idToClose && updatedTabs.length > 0) {
      setActiveTab(updatedTabs[updatedTabs.length - 1].id);
    }
  };

  useEffect(() => {
    if (activeTab && !terminalsRef.current[activeTab]) {
      const term = new Terminal({
        cursorBlink: true,
        theme: { background: '#000000', foreground: '#00ff00' },
        fontFamily: 'monospace',
        fontSize: 16
      });

      const fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      term.open(terminalContainerRef.current);
      fitAddon.fit();
      term.writeln(`=== In-App Terminal: Session ${activeTab} ===\r\n`);

      terminalsRef.current[activeTab] = term;

      RealTerminalPlugin.startShell({ shellId: activeTab });

      term.onData((data) => {
        RealTerminalPlugin.writeToShell({ shellId: activeTab, input: data });
      });
    }

    Object.keys(terminalsRef.current).forEach(id => {
      const element = terminalsRef.current[id].element;
      if (element) {
        element.style.display = parseInt(id) === activeTab ? 'block' : 'none';
      }
    });

  }, [activeTab, tabs]);

  useEffect(() => {
    const listener = RealTerminalPlugin.addListener('onShellOutput', (data) => {
      const targetShellId = data.shellId;
      if (terminalsRef.current[targetShellId]) {
        terminalsRef.current[targetShellId].write(data.output);
      }
    });
    return () => listener.remove();
  }, []);

  return (
    <div style={{ background: '#1e1e1e', height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', background: '#2d2d2d', padding: '10px', gap: '5px', overflowX: 'auto', alignItems: 'center' }}>
        {tabs.map(tab => (
          <div 
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            style={{
              background: tab.id === activeTab ? '#000000' : '#444444',
              color: tab.id === activeTab ? '#00ff00' : '#ffffff',
              padding: '6px 15px',
              borderRadius: '4px',
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              gap: '10px',
              fontSize: '14px'
            }}
          >
            <span>{tab.name}</span>
            <span onClick={(e) => closeTab(tab.id, e)} style={{ color: 'red', fontWeight: 'bold', marginLeft: '5px' }}>×</span>
          </div>
        ))}
        <button onClick={addNewTab} style={{ background: '#00ff00', color: '#000', border: 'none', padding: '6px 12px', borderRadius: '4px', fontWeight: 'bold', cursor: 'pointer' }}>+ New Tab</button>
      </div>
      <div ref={terminalContainerRef} style={{ flex: 1, background: '#000', padding: '10px' }} />
    </div>
  );
}
