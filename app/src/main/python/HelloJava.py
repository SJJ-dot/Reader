from Log import log

# python调用Java类
def get_java_bean(args):
    log(f"hello:{args}")
    return f"LogLog:{args}"
