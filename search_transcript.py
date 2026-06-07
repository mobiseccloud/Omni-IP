import json

with open('C:/Users/notif/.gemini/antigravity/brain/1d1f08ec-d72c-44d5-867e-4dc72d86a816/.system_generated/logs/transcript.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        if 'executeSecuritySweep' in line or 'isRaspCompromised' in line:
            print("FOUND:", line[:500])
