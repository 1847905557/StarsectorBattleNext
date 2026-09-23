import os


def main():
    root = os.path.dirname(os.path.abspath(__file__))
    english = os.path.isfile(os.path.join(root, "mod_info_ENG.json"))
    remove_suffix = "_ENG" if english else "_CN"
    append_suffix = "_CN" if english else "_ENG"
    swapped_folders = 0
    swapped_files = 0
    print("Swapping language resources: " + ("ENG -> CN" if english else "CN -> ENG"))

    for current_root, folders, filenames in os.walk(root):
        for folder in list(folders):
            if not folder.endswith(remove_suffix):
                continue
            base = folder[:-len(remove_suffix)]
            base_path = os.path.join(current_root, base)
            suffixed_path = os.path.join(current_root, folder)
            os.rename(base_path, base_path + append_suffix)
            os.rename(suffixed_path, base_path)
            swapped_folders += 1

        for filename in filenames:
            stem, extension = os.path.splitext(filename)
            if not stem.endswith(remove_suffix):
                continue
            base = stem[:-len(remove_suffix)]
            base_path = os.path.join(current_root, base + extension)
            suffixed_path = os.path.join(current_root, filename)
            os.rename(base_path, base_path[:-len(extension)] + append_suffix + extension)
            os.rename(suffixed_path, base_path)
            swapped_files += 1
    print("Swapped " + str(swapped_folders) + " directories and " + str(swapped_files) + " files.")


if __name__ == "__main__":
    main()
