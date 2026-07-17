import copy
import json


_namespace_cache = {}


def _build_namespace(script):
    ns = _namespace_cache.get(script)
    if ns is not None:
        return ns
    safe_builtins = copy.copy(__builtins__.__dict__) if hasattr(__builtins__, '__dict__') else copy.copy(__builtins__)
    namespace = {'__builtins__': safe_builtins}
    exec(script, namespace)
    _namespace_cache[script] = namespace
    return namespace


def has_function(script, func):
    namespace = _build_namespace(script)
    target = namespace.get(func)
    return callable(target)


def exec_script(script, func, arg=None):
    # 1. 准备一个感觉的运行环境
    namespace = _build_namespace(script)
    if arg is None:
        result = namespace[func]()
    else:
        result = namespace[func](arg)
    if isinstance(result, dict):
        json_str = json.dumps(result, ensure_ascii=False)
    else:
        json_str = result
    return json_str