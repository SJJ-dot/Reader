import os
import importlib.util
from log import log


def test(filename):
    # 创建模块名，去掉.py后缀
    module_name = filename[:-3].split("/")[-1]

    # 创建模块的完整路径

    # 创建模块规范
    spec = importlib.util.spec_from_file_location(module_name, filename)

    # 加载模块
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    # 调用模块中的search函数
    result = module.search("我的")
    log(result)
    result = module.getDetails(result[0]["bookUrl"])
    log(result)
    result = module.getChapterContent(result["chapterList"][0]["url"])
    log(result)
    result.replace("", "")


def test_source():
    test("./source.py")


def test_all():
    err_list = []
    # 指定.py文件所在的目录
    directory = "../BookSource/py"
    # 遍历目录下的所有.py文件
    for filename in os.listdir(directory):
        if filename.endswith(".py"):
            # 调用模块中的search函数
            try:
                test(os.path.join(directory, filename))
            except Exception as e:
                log(f"Error in module {filename}: {e}")  # 打印出异常信息
                err_list.append(filename)
                pass
    log(err_list)


if __name__ == '__main__':
    test_source()
