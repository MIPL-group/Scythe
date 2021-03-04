import os
import multiprocessing

def Find_File(root = "data", isfile = True):
    return [os.path.join(root,folder.name) for folder in os.scandir(root) if isfile ^ folder.is_dir()]


def run(file):
    os.system("java -jar Scythe.jar " + file + " StagedEnumerator >> out.txt")

def main():
    folders = Find_File("data",isfile=False)
    files = [] # the list of the path of all data files
    for folder in folders:
        files.extend(Find_File(folder,isfile=True))
    f = open("out.txt", "w")
    f.write("Begin\n")
    f.close()
    f = open("log.txt", "w")
    f.write("Errors:\n")
    for file in files:
        print(file)
        if not "dev_set/032X" in file:
            continue
        os.system("echo "+str(files.index(file))+"/"+str(len(files)))
        file = file.replace("\\", "/")
        try:
            p = multiprocessing.Process(target=run, args=(file,))
            p.start()
            p.join(60)

            if (p.is_alive()):
                p.kill()
                p.join()
                f.write(file)
                f.write("\n")
        except:
            f.write(file)
            f.write("\n")
    f.close()
if __name__ =="__main__":
    main()