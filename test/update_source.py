import base64
import gzip
import json
import os


def write_source(name, script):
    # 将汉字转换为拼音
    # 将脚本写入文件
    with open(f"./py/{name}.py", "w", encoding="utf-8") as f:
        f.write(script)


def update_json():
    # 读取json文件
    with open("./BookSource/default.json", "r", encoding="utf-8") as f:
        json1 = f.read()
    # 将json字符串转换为字典
    json_dict = json.loads(json1)
    pySourceDict = {}
    for item in json_dict["pySource"]:
        pySourceDict[item["source"]] = item

    for item in os.listdir("./py"):
        if not os.path.isfile(f"./py/{item}"):
            continue
        with open(f"./py/{item}", "r", encoding="utf-8") as f:
            py = f.read()
        if item.replace(".py", "") not in pySourceDict:
            json_dict["pySource"].append({
                "source": item.replace(".py", ""),
                "js": py,
                "version": 1,
                "original": False,
                "enable": True,
                "requestDelay": -1,
                "website": ""
            })
        else:
            source = pySourceDict[item.replace(".py", "")]
            if source["js"] != py:
                source["js"] = py
                source["version"] = source["version"] + 1
            pySourceDict.pop(item.replace(".py", ""))

    for item in pySourceDict:
        json_dict["pySource"].remove(pySourceDict[item])

    # 将字典转换为json字符串 并写入文件
    with open("./BookSource/default.json", "w", encoding="utf-8") as f:
        f.write(json.dumps(json_dict, indent=4, ensure_ascii=False))

    # 将字典转换为json字符串 使用gzip压缩并转换base64 后 写入文件
    with open("./BookSource/default.json.gzip", 'w', encoding="utf-8") as f:
        f.write(base64.b64encode(gzip.compress(json.dumps(json_dict).encode("utf-8"))).decode("utf-8"))


if __name__ == '__main__':
    # with open("source.py", "r", encoding="utf-8") as f:
    #     source = f.read()
    # source = source.split("if __name__ ==")[0]
    # write_source("飘天🪜", source)
    update_json()
