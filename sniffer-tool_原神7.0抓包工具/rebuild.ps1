$ErrorActionPreference = 'Continue'
Set-Item Env:RUSTC_BOOTSTRAP '1'
Set-Item Env:LIBPCAP_LIBDIR 'D:\LunaGC-7.0\AAA\npcap-sdk\Lib\x64'
Set-Item Env:LIBPCAP_VER '1.9.1'
Set-Location 'D:\sniffer-tool\sniffer-tool-master'

# 重新构建（protoshark 切到本地 path 后需重编译）
& 'D:\RuanJian\cargo\bin\cargo.exe' build --release 2>&1 | ForEach-Object { Write-Output $_ }
if ($LASTEXITCODE -ne 0) { Write-Output ('BUILD FAILED: ' + $LASTEXITCODE); exit 1 }
Write-Output 'BUILD OK'

# 部署到根目录（旧 exe 已有 .github-orig.bak 备份，直接覆盖）
Copy-Item 'D:\sniffer-tool\sniffer-tool-master\target\release\sniffer-tool.exe' 'D:\sniffer-tool\sniffer-tool-master\sniffer-tool.exe' -Force
Write-Output 'DEPLOYED to sniffer-tool-master\sniffer-tool.exe'

# 校验：构建产物确认引用了本地 protoshark
$p = 'D:\sniffer-tool\sniffer-tool-master\target\release\deps\libprotoshark-*.rlib'
Get-ChildItem $p | Select-Object Name, LastWriteTime, Length | Format-Table -AutoSize
