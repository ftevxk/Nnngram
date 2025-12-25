# WSL环境配置指令

## 目标
在新的Windows电脑上配置WSL环境，用于运行包含NDK/CMake组件的Android项目。

## 前置条件
- Windows 10 2004版本或更高，或Windows 11
- 管理员权限
- 稳定的网络连接

## 步骤1: 安装WSL和deepin分发版

### 1.1 启用WSL功能
以管理员身份打开PowerShell，执行以下命令：
```powershell
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
wsl --set-default-version 2
```

### 1.2 安装deepin分发版
打开Microsoft Store，搜索并安装"deepin"。

### 1.3 初始化deepin
安装完成后，启动deepin并完成初始化：
- 输入用户名: ``
- 输入密码: ``
- 确认密码: ``

## 步骤2: 安装Java开发环境

在deepin终端中执行以下命令：
```bash
# 更新系统包
apt-get update && apt-get upgrade -y

# 安装OpenJDK 17
apt-get install -y openjdk-17-jdk

# 验证安装
java -version
javac -version
```

## 步骤3: 安装Android开发环境

### 3.1 创建SDK目录
```bash
mkdir -p ~/Android/Sdk ~/Android/Sdk/cmdline-tools/latest
```

### 3.2 下载并安装SDK Command Line Tools
```bash
# 下载SDK工具包
wget https://dl.google.com/android/repository/commandlinetools-linux-10406996_latest.zip -O ~/android-sdk-tools.zip

# 解压到指定目录
unzip ~/android-sdk-tools.zip -d ~/Android/Sdk/cmdline-tools/
mv ~/Android/Sdk/cmdline-tools/cmdline-tools/* ~/Android/Sdk/cmdline-tools/latest/
rm -rf ~/Android/Sdk/cmdline-tools/cmdline-tools
rm ~/android-sdk-tools.zip
```

### 3.3 创建SDK安装脚本
```bash
# 创建安装脚本
cat > ~/setup_android_sdk.sh << 'EOF'
#!/bin/bash
yes | /home/ftevxk/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/home/ftevxk/Android/Sdk 'platform-tools' 'build-tools;36.1.0' 'platforms;android-36' 'ndk;28.1.13356709' 'cmake;4.1.2'
EOF

# 赋予执行权限
chmod +x ~/setup_android_sdk.sh

# 运行安装脚本
./setup_android_sdk.sh
```

## 步骤4: 配置环境变量

### 4.1 编辑.bashrc文件
```bash
cat >> ~/.bashrc << 'EOF'

# Java Environment Variables
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$PATH:$JAVA_HOME/bin

# Android Environment Variables
export ANDROID_HOME=~/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools
EOF

# 使配置生效
source ~/.bashrc
```

## 步骤5: 配置项目

### 5.1 挂载Windows项目目录
```bash
# 确保Windows项目目录存在
mkdir -p ~/Nnngram

# 创建符号链接（将Windows路径改为实际路径）
ln -s /mnt/c/*Windows路径* ~/Nnngram
```

## 步骤6: 配置端口转发

创建并运行端口转发脚本：
```powershell
# 在Windows PowerShell中执行

# 创建端口转发脚本
@'
# WSL Port Forwarding Configuration Script
# Define ports to forward
$generalPorts = @(80, 443, 8080, 3000, 5000)
$adbPorts = @(5037, 8554, 8555, 8556, 8557, 8558)
$allPorts = $generalPorts + $adbPorts

# Get WSL IP address
$wslIp = (wsl -d deepin -u ftevxk bash -c "ip addr show eth0 | grep 'inet ' | awk '{print $2}' | cut -d'/' -f1")

Write-Host "WSL IP Address: $wslIp"

# Configure port forwarding rules
foreach ($port in $allPorts) {
    Write-Host "Configuring port $port forwarding to WSL..."
    
    # Delete existing forwarding rule
    netsh interface portproxy delete v4tov4 listenport=$port listenaddress=0.0.0.0 2>$null
    
    # Add new forwarding rule
    netsh interface portproxy add v4tov4 listenport=$port listenaddress=0.0.0.0 connectport=$port connectaddress=$wslIp
    
    # Configure firewall rule
    netsh advfirewall firewall delete rule name="WSL Port Forwarding $port" dir=in protocol=tcp localport=$port 2>$null
    netsh advfirewall firewall add rule name="WSL Port Forwarding $port" dir=in action=allow protocol=tcp localport=$port
}

Write-Host "Port forwarding configuration completed!"

# Show current forwarding rules
Write-Host "\nCurrent port forwarding rules:"
netsh interface portproxy show v4tov4
'@ > "$env:USERPROFILE\Desktop\setup_wsl_ports.ps1"

# 以管理员身份运行脚本
Start-Process powershell -ArgumentList '-ExecutionPolicy Bypass -File "$env:USERPROFILE\Desktop\setup_wsl_ports.ps1"' -Verb RunAs
```

## 步骤7: 验证项目构建

### 7.1 进入项目目录
```bash
cd ~/Nnngram
```

### 7.2 执行构建命令
```bash
# 构建项目
./gradlew build --no-daemon

# 如果遇到SSL握手问题，可以尝试添加以下参数
# ./gradlew build --no-daemon -Dhttp.ssl.insecure=true -Dhttps.ssl.insecure=true
```

### 7.3 验证NDK/CMake编译
```bash
# 检查NDK版本
ndk-build --version

# 检查CMake版本
cmake --version

# 编译NDK组件
./gradlew assembleDebug
```

## 常见问题排查

### 问题1: SDK安装失败
**解决方案**：
1. 检查网络连接
2. 尝试手动安装SDK组件：
   ```bash
   sdkmanager --sdk_root=~/Android/Sdk --list
   sdkmanager --sdk_root=~/Android/Sdk --install 'platform-tools' 'build-tools;36.1.0'
   ```

### 问题2: 端口转发不生效
**解决方案**：
1. 确保以管理员身份运行PowerShell
2. 检查WSL IP地址是否正确
3. 手动添加端口转发规则：
   ```powershell
   $wslIp = "172.21.45.110"  # 替换为实际WSL IP
   netsh interface portproxy add v4tov4 listenport=5037 listenaddress=0.0.0.0 connectport=5037 connectaddress=$wslIp
   ```

### 问题3: 项目构建失败
**解决方案**：
1. 检查环境变量配置是否正确
2. 验证Android SDK和NDK版本是否匹配
3. 尝试清理构建缓存：
   ```bash
   ./gradlew clean build --no-daemon
   ```

## 完成
完成以上步骤后，项目应该可以在WSL环境中正常编译和运行。
