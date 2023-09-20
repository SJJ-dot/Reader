from log import log
# 创建一个空的命名空间
namespace = {}


def exec_script(script, func, arg):
    exec(script, namespace)
    return namespace[func](arg)
