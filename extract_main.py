import json

with open('C:/Users/notif/.gemini/antigravity/brain/1d1f08ec-d72c-44d5-867e-4dc72d86a816/.system_generated/logs/transcript.jsonl', 'r', encoding='utf-8') as f:
    for line in f:
        try:
            data = json.loads(line)
            if 'tool_calls' in data:
                for call in data['tool_calls']:
                    if call['function']['name'] == 'write_to_file':
                        args = json.loads(call['function']['arguments'])
                        if 'MainActivity.kt' in args.get('TargetFile', ''):
                            print('FOUND MainActivity.kt WRITE')
                            with open('original_main_activity.kt', 'w', encoding='utf-8') as out:
                                out.write(args['CodeContent'])
        except Exception as e:
            pass
