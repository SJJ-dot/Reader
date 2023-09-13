from com.sjianjun.reader.utils import PyLog as Log

def greet(name):
    print("--- hello,%s ---" % name)

def add(a,b):
    return a + b

def sub(count,a=0,b=0,c=0):
    return count - a - b -c

def get_list(a,b,c,d):
    return [a,b,c,d]

def print_list(data):
    print(type(data))
    # 遍历Java的ArrayList对象
    for i in range(data.size()):
        print(data.get(i))

# python调用Java类
def get_java_bean(args):
    Log.e(f"python调用Java类:{args}")
    return f"LogLog:{args}"
