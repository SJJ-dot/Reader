import copy
import json
def exec_script(script, func, arg=None):
    # 1. 准备安全环境
    safe_builtins = copy.copy(__builtins__.__dict__) if hasattr(__builtins__, '__dict__') else copy.copy(__builtins__)
    namespace = {'__builtins__': safe_builtins}
    exec(script, namespace)
    if arg is None:
        result = namespace[func]()
    else:
        result = namespace[func](arg)
    if isinstance(result, dict):
        json_str = json.dumps(result, ensure_ascii=False)
    else:
        json_str = result
    return json_str