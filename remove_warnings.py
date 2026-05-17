import os
import re

ui_dir = r"d:\MHUD\Project\src\client-app\src\main\java\vn\edu\hcmus\securechat\client\ui"
files = [
    r"components\AvatarLabel.java",
    r"LoginDialog.java",
    r"components\PrimaryButton.java",
    r"components\RoundedPanel.java",
    r"RegisterPanel.java",
    r"MainFrame.java",
    r"components\AuditLogTable.java",
    r"UiStyles.java",
    r"ChatPanel.java",
    r"SecurityMonitorPanel.java",
    r"LoginPanel.java",
    r"..\ClientApp.java"
]

for f in files:
    path = os.path.join(ui_dir, f)
    if not os.path.exists(path): continue
    with open(path, 'r', encoding='utf-8') as file:
        content = file.read()
    
    # Remove all occurrences of the specific SuppressWarnings
    content = content.replace('@SuppressWarnings({"serial", "this-escape"})\n', '')
    content = content.replace('    @SuppressWarnings({"serial", "this-escape"})\n', '')
    
    with open(path, 'w', encoding='utf-8') as file:
        file.write(content)
