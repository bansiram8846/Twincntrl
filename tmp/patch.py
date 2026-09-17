import re

# 1. Patch TargetInviteServer.smali
server_smali_path = "/tmp/apktool_out/smali_classes9/com/example/network/server/TargetInviteServer.smali"
with open(server_smali_path, "r") as f:
    server_smali = f.read()

new_serve_join_html = """\
.method private final serveJoinHtml(Ljava/io/OutputStream;Ljava/lang/String;)V
    .locals 9
    .param p1, "out"    # Ljava/io/OutputStream;
    .param p2, "remoteIp"    # Ljava/lang/String;

    .line 212
    sget-object v0, Lcom/example/network/LocalDeviceManager;->INSTANCE:Lcom/example/network/LocalDeviceManager;
    iget-object v1, p0, Lcom/example/network/server/TargetInviteServer;->context:Landroid/content/Context;
    invoke-virtual {v0, v1}, Lcom/example/network/LocalDeviceManager;->getLocalIpAddress(Landroid/content/Context;)Ljava/lang/String;
    move-result-object v0

    .line 213
    .local v0, "controllerIp":Ljava/lang/String;
    sget-object v1, Lcom/example/network/LocalDeviceManager;->INSTANCE:Lcom/example/network/LocalDeviceManager;
    iget-object v2, p0, Lcom/example/network/server/TargetInviteServer;->context:Landroid/content/Context;
    invoke-virtual {v1, v2}, Lcom/example/network/LocalDeviceManager;->getEffectiveDeviceName(Landroid/content/Context;)Ljava/lang/String;
    move-result-object v1

    .line 214
    .local v1, "controllerName":Ljava/lang/String;
    new-instance v3, Ljava/lang/StringBuilder;
    invoke-direct {v3}, Ljava/lang/StringBuilder;-><init>()V

    const-string v4, "<!DOCTYPE html><html lang=\\"en\\"><head><meta charset=\\"UTF-8\\"><meta name=\\"viewport\\" content=\\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\\"><title>TwinControl Target Mode</title><style>*{box-sizing:border-box;margin:0;padding:0;}body{font-family:-apple-system,BlinkMacSystemFont,\\'Segoe UI\\',Roboto,sans-serif;background:#0d0f12;color:#f3f4f6;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:16px;}.card{background:#16191f;border:1px solid #282d37;border-radius:24px;padding:24px 20px;width:100%;max-width:440px;text-align:center;box-shadow:0 20px 50px rgba(0,0,0,0.7);}.badge{display:inline-flex;align-items:center;gap:6px;background:rgba(34,197,94,0.15);border:1px solid #22c55e;border-radius:20px;padding:5px 14px;font-size:12px;color:#4ade80;font-weight:600;margin-bottom:12px;}.pulse{width:8px;height:8px;border-radius:50%;background:#22c55e;animation:blink 1.5s infinite ease-in-out;}@keyframes blink{0%,100%{opacity:1;transform:scale(1);}50%{opacity:0.3;transform:scale(0.85);}}.box{background:#1e232b;border-radius:14px;padding:12px 14px;margin:12px 0;text-align:left;font-size:13px;}.box-title{font-size:11px;text-transform:uppercase;letter-spacing:0.5px;color:#9ca3af;margin-bottom:4px;}.box-val{font-size:14px;font-weight:600;color:#fff;}.desc{font-size:13px;color:#9ca3af;line-height:1.5;margin:12px 0;}.btn-share{width:100%;border:none;outline:none;background:#2563eb;color:#fff;font-size:15px;font-weight:700;padding:14px 16px;border-radius:14px;cursor:pointer;display:flex;align-items:center;justify-content:center;gap:8px;box-shadow:0 4px 16px rgba(37,99,235,0.4);}.btn-share:active{transform:scale(0.98);}.btn-stop{background:#dc2626!important;box-shadow:0 4px 16px rgba(220,38,38,0.4)!important;}video,canvas{width:100%;border-radius:12px;margin-top:12px;border:1px solid #374151;}.hint{margin-top:14px;font-size:11px;color:#6b7280;}</style></head><body><div class=\\"card\\"><div style=\\"font-size:36px;margin-bottom:8px;\\">\\ud83d\\udcf1</div><h1 style=\\"font-size:20px;font-weight:700;margin-bottom:8px;\\">Target Mode Active</h1><div class=\\"badge\\"><span class=\\"pulse\\"></span> Active & Available in Controller</div><div class=\\"box\\"><div class=\\"box-title\\">Connected Controller</div><div class=\\"box-val\\">"
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v1}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v4, "</div><div style=\\"font-family:monospace;font-size:12px;color:#9ca3af;margin-top:2px;\\">"
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, v0}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v4, "</div></div><div class=\\"box\\"><div class=\\"box-title\\">Target Device IP</div><div class=\\"box-val\\" style=\\"font-family:monospace;color:#60a5fa;\\">"
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v4, "</div></div><p class=\\"desc\\">This mobile is directly connected to the controller. No app installation is required on this phone.</p><button id=\\"shareBtn\\" class=\\"btn-share\\" onclick=\\"toggleScreen()\\"><span>\\ud83d\\udcfa</span> Share Screen to Controller</button><video id=\\"vid\\" autoplay playsinline muted style=\\"display:none;\\"></video><canvas id=\\"cnv\\" style=\\"display:none;\\"></canvas><div id=\\"liveStatus\\" style=\\"display:none;color:#22c55e;font-size:12px;font-weight:600;margin-top:8px;\\">\\u25cf Live Screen Stream Active</div><div class=\\"hint\\">Keep this browser tab open to maintain the target link.</div></div><script>var clientIp=\\'"
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    const-string v4, "\\';function registerTarget(){var ua=navigator.userAgent;var name=\\'Mobile Target\\';var model=\\'Web Browser\\';if(/Android/i.test(ua)){name=\\'Android Mobile\\';model=\\'Android Web\\';}else if(/iPhone|iPad/i.test(ua)){name=\\'iPhone Mobile\\';model=\\'iOS Safari\\';}fetch(\\'/api/target_ready?ip=\\'+encodeURIComponent(clientIp)+\\'&name=\\'+encodeURIComponent(name)+\\'&model=\\'+encodeURIComponent(model)+\\'&port=8090\\').catch(function(){});}registerTarget();setInterval(function(){fetch(\\'/api/target_ping?ip=\\'+encodeURIComponent(clientIp)+\\'&agent=\\'+encodeURIComponent(navigator.userAgent)).catch(function(){});},2500);var isStreaming=false;var timer=null;var vid=document.getElementById(\\'vid\\');var cnv=document.getElementById(\\'cnv\\');var btn=document.getElementById(\\'shareBtn\\');var st=document.getElementById(\\'liveStatus\\');async function toggleScreen(){if(isStreaming){isStreaming=false;clearInterval(timer);if(vid.srcObject){vid.srcObject.getTracks().forEach(function(t){t.stop();});}btn.innerHTML=\\'<span>\\ud83d\\udcfa</span> Share Screen to Controller\\';btn.className=\\'btn-share\\';cnv.style.display=\\'none\\';st.style.display=\\'none\\';return;}try{var s=null;if(navigator.mediaDevices&&navigator.mediaDevices.getDisplayMedia){s=await navigator.mediaDevices.getDisplayMedia({video:true,audio:false});}else if(navigator.mediaDevices&&navigator.mediaDevices.getUserMedia){s=await navigator.mediaDevices.getUserMedia({video:{facingMode:\\'user\\'}});}if(s){vid.srcObject=s;vid.play();isStreaming=true;btn.innerHTML=\\'<span>\\u23f9\\ufe0f</span> Stop Screen Share\\';btn.className=\\'btn-share btn-stop\\';cnv.style.display=\\'block\\';st.style.display=\\'block\\';timer=setInterval(function(){if(!isStreaming)return;var w=vid.videoWidth||640;var h=vid.videoHeight||480;cnv.width=Math.min(w,720);cnv.height=Math.round(cnv.width*(h/(w||1)));var ctx=cnv.getContext(\\'2d\\');ctx.drawImage(vid,0,0,cnv.width,cnv.height);},200);}}catch(e){alert(\\'Screen share notice: \\'+(e.message||e));}}</script></body></html>"
    invoke-virtual {v3, v4}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    invoke-virtual {v3}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v3

    .local v3, "html":Ljava/lang/String;
    sget-object v4, Lkotlin/text/Charsets;->UTF_8:Ljava/nio/charset/Charset;
    invoke-virtual {v3, v4}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B
    move-result-object v4
    const-string v5, "getBytes(...)"
    invoke-static {v4, v5}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V

    .local v4, "bytes":[B
    array-length v6, v4
    new-instance v7, Ljava/lang/StringBuilder;
    invoke-direct {v7}, Ljava/lang/StringBuilder;-><init>()V
    const-string v8, "HTTP/1.1 200 OK\\r\\nContent-Type: text/html; charset=UTF-8\\r\\nContent-Length: "
    invoke-virtual {v7, v8}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v7
    invoke-virtual {v7, v6}, Ljava/lang/StringBuilder;->append(I)Ljava/lang/StringBuilder;
    move-result-object v6
    const-string v7, "\\r\\nConnection: close\\r\\n\\r\\n"
    invoke-virtual {v6, v7}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;
    move-result-object v6
    invoke-virtual {v6}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;
    move-result-object v6

    .local v6, "headers":Ljava/lang/String;
    sget-object v7, Lkotlin/text/Charsets;->UTF_8:Ljava/nio/charset/Charset;
    invoke-virtual {v6, v7}, Ljava/lang/String;->getBytes(Ljava/nio/charset/Charset;)[B
    move-result-object v7
    invoke-static {v7, v5}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullExpressionValue(Ljava/lang/Object;Ljava/lang/String;)V
    invoke-virtual {p1, v7}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {p1, v4}, Ljava/io/OutputStream;->write([B)V

    invoke-virtual {p1}, Ljava/io/OutputStream;->flush()V

    return-void
.end method"""

start_marker = ".method private final serveJoinHtml(Ljava/io/OutputStream;Ljava/lang/String;)V"
end_marker = ".end method"

idx_start = server_smali.find(start_marker)
assert idx_start != -1, "start_marker not found"
idx_end = server_smali.find(end_marker, idx_start)
assert idx_end != -1, "end_marker not found"
idx_end += len(end_marker)

server_smali = server_smali[:idx_start] + new_serve_join_html + server_smali[idx_end:]

with open(server_smali_path, "w") as f:
    f.write(server_smali)
print("Updated TargetInviteServer.smali successfully!")

# 2. Patch ControllerViewModel$1$1.smali
vm1_path = "/tmp/apktool_out/smali_classes8/com/example/ui/controller/ControllerViewModel$1$1.smali"
with open(vm1_path, "r") as f:
    vm1_smali = f.read()

target_anchor = "invoke-static {v0, v1}, Lcom/example/ui/controller/ControllerViewModel;->access$savePairedDevice(Lcom/example/ui/controller/ControllerViewModel;Lcom/example/data/model/DeviceInfo;)V"
if target_anchor in vm1_smali and "sget-object v1, Lcom/example/data/model/ConnectionState;->CONNECTED:Lcom/example/data/model/ConnectionState;" not in vm1_smali:
    insertion = """\
    iget-object v0, p0, Lcom/example/ui/controller/ControllerViewModel$1$1;->this$0:Lcom/example/ui/controller/ControllerViewModel;
    invoke-static {v0}, Lcom/example/ui/controller/ControllerViewModel;->access$get_connectionState$p(Lcom/example/ui/controller/ControllerViewModel;)Lkotlinx/coroutines/flow/MutableStateFlow;
    move-result-object v0
    sget-object v1, Lcom/example/data/model/ConnectionState;->CONNECTED:Lcom/example/data/model/ConnectionState;
    invoke-interface {v0, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    iget-object v0, p0, Lcom/example/ui/controller/ControllerViewModel$1$1;->this$0:Lcom/example/ui/controller/ControllerViewModel;
    invoke-static {v0}, Lcom/example/ui/controller/ControllerViewModel;->access$get_pairingState$p(Lcom/example/ui/controller/ControllerViewModel;)Lkotlinx/coroutines/flow/MutableStateFlow;
    move-result-object v0
    sget-object v1, Lcom/example/data/model/PairingState;->PAIRED:Lcom/example/data/model/PairingState;
    invoke-interface {v0, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V
"""
    vm1_smali = vm1_smali.replace(target_anchor, target_anchor + "\n\n" + insertion)
    with open(vm1_path, "w") as f:
        f.write(vm1_smali)
    print("Updated ControllerViewModel$1$1.smali successfully!")

# 3. Patch ControllerViewModel.smali startMonitoringTarget
vm_path = "/tmp/apktool_out/smali_classes8/com/example/ui/controller/ControllerViewModel.smali"
with open(vm_path, "r") as f:
    vm_smali = f.read()

old_monitor = """\
    .line 235
    const-string v0, "AUTO_TRUSTED"

    const/4 v1, 0x1

    invoke-virtual {p0, p1, v0, v1}, Lcom/example/ui/controller/ControllerViewModel;->pairWithDevice(Lcom/example/data/model/DeviceInfo;Ljava/lang/String;Z)V"""

new_monitor = """\
    iget-object v0, p0, Lcom/example/ui/controller/ControllerViewModel;->_connectionState:Lkotlinx/coroutines/flow/MutableStateFlow;

    sget-object v1, Lcom/example/data/model/ConnectionState;->CONNECTED:Lcom/example/data/model/ConnectionState;

    invoke-interface {v0, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V

    iget-object v0, p0, Lcom/example/ui/controller/ControllerViewModel;->_pairingState:Lkotlinx/coroutines/flow/MutableStateFlow;

    sget-object v1, Lcom/example/data/model/PairingState;->PAIRED:Lcom/example/data/model/PairingState;

    invoke-interface {v0, v1}, Lkotlinx/coroutines/flow/MutableStateFlow;->setValue(Ljava/lang/Object;)V"""

if old_monitor in vm_smali:
    vm_smali = vm_smali.replace(old_monitor, new_monitor)
    with open(vm_path, "w") as f:
        f.write(vm_smali)
    print("Updated startMonitoringTarget in ControllerViewModel.smali successfully!")
else:
    print("old_monitor already updated or not found")
