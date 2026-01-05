# 1. 安装 WSL 并初始化
wsl --install -d Ubuntu-22.04
wsl -u root -e bash -c "apt-get update && apt-get install -y sudo"

# 2. 设置 root 免密（仅本地开发机，生产勿用）
wsl -u root -e bash -c "sed -i 's/^root:.*/root::0:0:root:\/root:\/bin\/bash/' /etc/passwd"

# 3. 映射 Windows 的 git 与 adb 到 WSL
cat >> ~/.bashrc <<'EOF'
export PATH="$PATH:/mnt/c/Program Files/Git/bin:/mnt/c/Platform-Tools"
EOF

# 4. 全局配置 git
git config --global user.name "ftevxk"
git config --global user.email "ftevxk@gmail.com"

# 5. 克隆项目并切换到 wd 分支
git clone https://github.com/ftevxk/Nnngram.git
cd Nnngram
git checkout wd

# 6. 依据 CI 文件安装依赖
sudo apt-get install -y $(awk -F: '/run:/{gsub(/apt-get install -y/,""); print}' .github/workflows/ci.yml)
