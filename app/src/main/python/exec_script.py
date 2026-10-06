import copy
import inspect
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


def _accepts_two_args(target):
    # 老脚本可能只声明了一个参数，需要兼容
    try:
        parameters = list(inspect.signature(target).parameters.values())
    except (TypeError, ValueError):
        return True
    if any(p.kind == p.VAR_POSITIONAL for p in parameters):
        return True
    positional = [p for p in parameters
                  if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)]
    return len(positional) >= 2


def exec_script(script, func, arg=None, arg2=None):
    # 1. 准备一个感觉的运行环境
    namespace = _build_namespace(script)
    target = namespace[func]
    if arg2 is None:
        if arg is None:
            result = target()
        else:
            result = target(arg)
    elif _accepts_two_args(target):
        result = target(arg, arg2)
    else:
        result = target(arg)
    if isinstance(result, dict):
        json_str = json.dumps(result, ensure_ascii=False)
    else:
        json_str = result
    return json_str