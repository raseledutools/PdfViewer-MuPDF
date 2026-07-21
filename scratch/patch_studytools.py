import os
import re

file_paths = [
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\selfcontrol\study_tools\study_tools.kt",
    r"D:\github web\RasFocus-final\Rasfocus-final\app\src\main\java\com\rasel\RasFocus\combo\selfcontrol\study_tools\study_tools.kt"
]

for file_path in file_paths:
    if not os.path.exists(file_path):
        continue
    
    with open(file_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Move Diary to the top
    diary_block = re.search(r'(        // ── Personal Diary ──.*?\n        SectionTitle\("📓 Personal Diary".*?\n        PersonalDiaryCard\(onClick = onOpenDiary\)\n)', content, flags=re.DOTALL)
    
    if diary_block:
        diary_str = diary_block.group(1)
        # Remove it from its original place
        content = content.replace(diary_str, "")
        
        # Find header end to insert after
        header_end_str = '        // ── Doc Scanner (CamScanner-style) ──────────────────────────────'
        if header_end_str in content:
            content = content.replace(header_end_str, diary_str + "\n" + header_end_str)

    # 2. Modify NativeToolCard
    native_card_block_match = re.search(r'private fun NativeToolCard.*?^}', content, flags=re.DOTALL | re.MULTILINE)
    if native_card_block_match:
        native_card_block = native_card_block_match.group(0)
        new_native_card = native_card_block
        new_native_card = new_native_card.replace("height(110.dp)", "height(90.dp)")
        new_native_card = new_native_card.replace("RoundedCornerShape(20.dp)", "RoundedCornerShape(16.dp)")
        new_native_card = new_native_card.replace("padding(16.dp)", "padding(12.dp)")
        new_native_card = new_native_card.replace("fontSize = 26.sp", "fontSize = 22.sp")
        new_native_card = new_native_card.replace("Spacer(Modifier.height(6.dp))", "Spacer(Modifier.height(4.dp))")
        content = content.replace(native_card_block, new_native_card)

    # 3. Modify ToolCard
    tool_card_block_match = re.search(r'private fun ToolCard\(.*?^}', content, flags=re.DOTALL | re.MULTILINE)
    if tool_card_block_match:
        tool_card_block = tool_card_block_match.group(0)
        new_tool_card = tool_card_block
        new_tool_card = new_tool_card.replace("height(110.dp)", "height(85.dp)")
        new_tool_card = new_tool_card.replace("RoundedCornerShape(20.dp)", "RoundedCornerShape(16.dp)")
        new_tool_card = new_tool_card.replace("padding(16.dp)", "padding(12.dp)")
        new_tool_card = new_tool_card.replace("fontSize = 26.sp", "fontSize = 20.sp")
        new_tool_card = new_tool_card.replace("Spacer(Modifier.height(8.dp))", "Spacer(Modifier.height(4.dp))")
        content = content.replace(tool_card_block, new_tool_card)

    with open(file_path, 'w', encoding='utf-8') as f:
        f.write(content)

print("Patch applied to study_tools.kt")
