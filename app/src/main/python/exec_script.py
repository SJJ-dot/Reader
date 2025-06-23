import copy

def exec_script(script, func, arg):
    # 1. 准备安全环境
    safe_builtins = copy.copy(__builtins__.__dict__) if hasattr(__builtins__, '__dict__') else copy.copy(__builtins__)
    namespace = {'__builtins__': safe_builtins}
    exec(script, namespace)
    return namespace[func](arg)
