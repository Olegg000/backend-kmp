import os
import argparse
import fnmatch
from datetime import datetime

# --- Конфигурация по умолчанию ---
DEFAULT_IGNORES = [
    "venv",  # Стандартные директории виртуальных окружений
    ".venv",
    "__pycache__",  # Кэш Python
    "*.pyc",  # Скомпилированные файлы Python
    "*.pyo",
    "run_dumper.sh",
    "run_dumper.py",
    ".git",  # Директория Git
    ".gitignore",
    ".idea",  # Настройки IDE (JetBrains)
    "*.egg-info",  # Метаданные пакетов Python
    "build",  # Стандартные директории сборки
    "dist",
    ".DS_Store",  # Файлы macOS
    "node_modules",  # Зависимости Node.js
    "*.log",  # Лог-файлы
    # Директория, куда будут сохраняться дампы, чтобы не включать их самих
    # Будет добавлена динамически на основе аргумента --output-dir
]
DEFAULT_IGNORE_FILE_NAME = ".project_copier_ignore"  # Имя файла игнорирования по умолчанию
DEFAULT_OUTPUT_DIR_NAME = "dumps"  # Имя директории для дампов по умолчанию

# Глобальная переменная для корректного построения относительных путей в дереве
# Устанавливается в main() на основе args.root_dir
abs_scan_root_dir = ""


def load_ignore_patterns(ignore_file_path, default_ignores_list, output_dir_name_to_ignore):
    """Загружает шаблоны игнорирования из файла и объединяет со стандартными."""
    patterns = set(default_ignores_list)
    patterns.add(output_dir_name_to_ignore + "/")  # Игнорировать папку с дампами
    patterns.add(output_dir_name_to_ignore)  # И на всякий случай без слеша

    if ignore_file_path and os.path.exists(ignore_file_path):
        try:
            with open(ignore_file_path, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith('#'):  # Пропускаем пустые строки и комментарии
                        patterns.add(line)
            print(f"INFO: Загружены шаблоны игнорирования из: {ignore_file_path}")
        except Exception as e:
            print(
                f"WARNING: Ошибка при чтении файла игнорирования '{ignore_file_path}': {e}. Будут использованы только стандартные шаблоны.")
    elif ignore_file_path:  # Если путь был указан, но файла нет
        print(
            f"WARNING: Файл игнорирования '{ignore_file_path}' не найден. Будут использованы только стандартные шаблоны.")

    print(f"INFO: Активные шаблоны игнорирования: {sorted(list(patterns))}")
    return list(patterns)


def should_ignore(item_name, item_full_path, root_dir_abs_path, patterns):
    """
    Проверяет, следует ли игнорировать элемент (файл или директорию).
    item_name: Имя файла или директории.
    item_full_path: Абсолютный путь к элементу.
    root_dir_abs_path: Абсолютный путь к корневой директории сканирования.
    patterns: Список шаблонов игнорирования.
    """
    # Получаем относительный путь элемента от корня сканирования
    try:
        relative_path = os.path.relpath(item_full_path, root_dir_abs_path)
    except ValueError:  # Может возникнуть, если item_full_path не является подпутем root_dir_abs_path (маловероятно при os.walk)
        relative_path = item_name

    for pattern in patterns:
        # 1. Проверка простого имени (например, "venv" или "*.log")
        if fnmatch.fnmatch(item_name, pattern):
            return True
        # 2. Проверка относительного пути
        #    Для директорий: "somedir/" должен матчить "somedir" и "somedir/"
        #    Для файлов: "somedir/file.txt"
        if pattern.endswith(os.path.sep):  # Шаблон для директории
            # Проверяем, начинается ли относительный путь с этого шаблона (или совпадает, если шаблон это просто "dir/")
            # Нормализуем пути для сравнения
            normalized_relative_path_with_sep = os.path.normpath(relative_path) + os.path.sep
            normalized_pattern = os.path.normpath(pattern)
            if normalized_relative_path_with_sep.startswith(normalized_pattern):
                return True
            # Также проверяем без слеша на конце для случая, если в ignore указано "somedir", а мы проверяем "somedir"
            if os.path.normpath(relative_path) == os.path.normpath(pattern.rstrip(os.path.sep)):
                return True
        elif fnmatch.fnmatch(relative_path, pattern):  # Шаблон для файла или полный путь к директории без слеша
            return True
    return False


def generate_directory_tree_lines(current_dir_abs_path, ignore_patterns, prefix=""):
    """Рекурсивно генерирует строки для дерева каталогов."""
    tree_lines = []
    try:
        # Получаем список элементов, сортируем (папки обычно первыми при стандартной сортировке имен)
        dir_items = sorted(os.listdir(current_dir_abs_path))
    except PermissionError:
        tree_lines.append(f"{prefix}└── [Отказано в доступе]")
        return tree_lines
    except FileNotFoundError:
        tree_lines.append(f"{prefix}└── [Директория не найдена]")
        return tree_lines

    # Отфильтрованные и отсортированные элементы для обработки
    valid_items_info = []
    for item_name in dir_items:
        item_full_abs_path = os.path.join(current_dir_abs_path, item_name)
        if not should_ignore(item_name, item_full_abs_path, abs_scan_root_dir, ignore_patterns):
            valid_items_info.append({
                "name": item_name,
                "path": item_full_abs_path,
                "is_dir": os.path.isdir(item_full_abs_path)
            })

    # Сортировка: сначала папки, потом файлы (по имени внутри групп)
    valid_items_info.sort(key=lambda x: (not x["is_dir"], x["name"].lower()))

    for i, item_info in enumerate(valid_items_info):
        is_last_item = (i == len(valid_items_info) - 1)
        connector = "└── " if is_last_item else "├── "
        tree_lines.append(f"{prefix}{connector}{item_info['name']}")

        if item_info["is_dir"]:
            new_prefix = prefix + ("    " if is_last_item else "│   ")
            tree_lines.extend(generate_directory_tree_lines(item_info["path"], ignore_patterns, new_prefix))
    return tree_lines


def main():
    parser = argparse.ArgumentParser(
        description="Собирает все файлы проекта в один TXT-файл, добавляя структуру каталогов и дополнительный текст.",
        formatter_class=argparse.RawTextHelpFormatter
    )
    parser.add_argument("root_dir",
                        help="Корневая директория проекта для сканирования (например, ./dataGetter).")
    parser.add_argument("--ignore-file", default=None,
                        help=f"Путь к файлу с шаблонами игнорирования (по умолчанию: {DEFAULT_IGNORE_FILE_NAME} в корневой директории проекта, если существует).")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR_NAME,
                        help=f"Директория для сохранения дамп-файла (по умолчанию: ./{DEFAULT_OUTPUT_DIR_NAME}).")
    parser.add_argument("--output-filename", default=None,
                        help="Имя дамп-файла (по умолчанию: project_dump_ГГГГММДД_ЧЧММСС.txt).")
    parser.add_argument("--append-text-file", default=None,
                        help="Путь к файлу, содержимое которого будет добавлено в самый конец дампа.")
    parser.add_argument("--no-default-ignores", action="store_true",
                        help="Не использовать стандартный список игнорируемых файлов/папок (см. DEFAULT_IGNORES в скрипте).")
    parser.add_argument("--encoding", default="utf-8",
                        help="Кодировка для чтения файлов проекта (по умолчанию: utf-8). Используйте 'latin-1' или др. при проблемах.")
    parser.add_argument("--error-handling", default="surrogateescape",
                        choices=['strict', 'ignore', 'replace', 'surrogateescape'],
                        help="Как обрабатывать ошибки кодировки при чтении файлов (по умолчанию: surrogateescape).")

    args = parser.parse_args()

    global abs_scan_root_dir  # Устанавливаем глобальную переменную
    abs_scan_root_dir = os.path.abspath(args.root_dir)

    if not os.path.isdir(abs_scan_root_dir):
        print(
            f"Ошибка: Указанная корневая директория '{args.root_dir}' (абсолютный путь: '{abs_scan_root_dir}') не существует или не является директорией.")
        return

    # Определяем путь к файлу игнорирования
    actual_ignore_file_path = args.ignore_file
    if actual_ignore_file_path is None:  # Если не указан явно, ищем по умолчанию в корне проекта
        actual_ignore_file_path = os.path.join(abs_scan_root_dir, DEFAULT_IGNORE_FILE_NAME)
        if not os.path.exists(actual_ignore_file_path):
            actual_ignore_file_path = None  # Файла по умолчанию нет

    default_ignores_list = [] if args.no_default_ignores else DEFAULT_IGNORES
    # Передаем имя папки для вывода, чтобы ее тоже игнорировать
    ignore_patterns = load_ignore_patterns(actual_ignore_file_path, default_ignores_list,
                                           os.path.basename(os.path.normpath(args.output_dir)))

    # Создание выходной директории
    abs_output_dir = os.path.abspath(args.output_dir)
    if not os.path.exists(abs_output_dir):
        try:
            os.makedirs(abs_output_dir)
            print(f"INFO: Создана директория для вывода: {abs_output_dir}")
        except Exception as e:
            print(f"КРИТИЧЕСКАЯ ОШИБКА: Не удалось создать директорию для вывода '{abs_output_dir}': {e}")
            return

    # Формирование имени выходного файла
    output_filename = args.output_filename
    if not output_filename:
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        project_name_slug = os.path.basename(abs_scan_root_dir).lower().replace(" ", "_")
        output_filename = f"{project_name_slug}_dump_{timestamp}.txt"
    output_file_abs_path = os.path.join(abs_output_dir, output_filename)

    # Получение абсолютного пути к текущему скрипту, чтобы его тоже игнорировать
    try:
        abs_script_path = os.path.abspath(__file__)
    except NameError:  # Если запускается интерактивно, __file__ может быть не определен
        abs_script_path = os.path.abspath(os.getcwd())  # Фоллбэк, не идеально
        print(
            f"WARNING: Не удалось определить путь к скрипту (__file__ не определен), используется CWD: {abs_script_path}")

    print(f"INFO: Начало обработки проекта в '{abs_scan_root_dir}'.")
    print(f"INFO: Вывод будет сохранен в '{output_file_abs_path}'.")

    try:
        with open(output_file_abs_path, 'w', encoding='utf-8') as outfile:
            # Обход файлов
            for root_abs_path, dirs, files in os.walk(abs_scan_root_dir, topdown=True):
                # Фильтрация директорий "на лету"
                # dirs[:] = [...] изменяет список dirs, чтобы os.walk не заходил в отфильтрованные
                original_dirs_count = len(dirs)
                dirs[:] = [d_name for d_name in dirs if
                           not should_ignore(d_name, os.path.join(root_abs_path, d_name), abs_scan_root_dir,
                                             ignore_patterns)]
                if len(dirs) < original_dirs_count:
                    print(
                        f"DEBUG: Отфильтровано {original_dirs_count - len(dirs)} поддиректорий в '{os.path.relpath(root_abs_path, abs_scan_root_dir)}'")

                for filename in sorted(files):  # Сортируем файлы для консистентного порядка
                    file_abs_path = os.path.join(root_abs_path, filename)

                    # Пропускаем сам скрипт и файл вывода
                    if file_abs_path == abs_script_path:
                        print(
                            f"DEBUG: Игнорируется сам исполняемый скрипт: {os.path.relpath(file_abs_path, abs_scan_root_dir)}")
                        continue
                    if file_abs_path == output_file_abs_path:
                        print(
                            f"DEBUG: Игнорируется сам файл вывода: {os.path.relpath(file_abs_path, abs_scan_root_dir)}")
                        continue

                    if should_ignore(filename, file_abs_path, abs_scan_root_dir, ignore_patterns):
                        # print(f"DEBUG: Игнорируется файл (по шаблону): {os.path.relpath(file_abs_path, abs_scan_root_dir)}")
                        continue

                    relative_file_path = os.path.relpath(file_abs_path, abs_scan_root_dir)
                    # Используем '/' в качестве разделителя для единообразия в выводе
                    header_path = relative_file_path.replace(os.path.sep, '/')

                    print(f"INFO: Обработка файла: {relative_file_path}")
                    outfile.write(
                        f"\n\n# ==============================================================================\n")
                    outfile.write(f"# Файл: {header_path}\n")
                    outfile.write(f"# Полный путь (относительно корня сканирования): {header_path}\n")
                    outfile.write(
                        f"# ==============================================================================\n\n")
                    try:
                        with open(file_abs_path, 'r', encoding=args.encoding, errors=args.error_handling) as infile:
                            outfile.write(infile.read())
                    except FileNotFoundError:
                        outfile.write(
                            f"# ОШИБКА: Файл не найден (возможно, удален во время работы скрипта): {header_path}\n")
                        print(f"ERROR: Файл не найден '{relative_file_path}'.")
                    except Exception as e:
                        outfile.write(
                            f"# ОШИБКА: Не удалось прочитать файл '{header_path}'. Кодировка: {args.encoding}, Ошибка: {e}\n")
                        print(f"ERROR: Ошибка чтения файла '{relative_file_path}' (кодировка: {args.encoding}): {e}")
                    outfile.write(f"\n\n# --- КОНЕЦ ФАЙЛА: {header_path} --- \n")

            # Добавление структуры директорий
            outfile.write("\n\n\n# ==============================================================================\n")
            outfile.write(f"# СТРУКТУРА ПРОЕКТА: {os.path.basename(abs_scan_root_dir)}\n")
            outfile.write("# ==============================================================================\n\n")

            project_root_display_name = os.path.basename(abs_scan_root_dir)
            outfile.write(f"{project_root_display_name}/\n")  # Корень дерева
            tree_lines = generate_directory_tree_lines(abs_scan_root_dir, ignore_patterns)
            for line in tree_lines:
                outfile.write(line + "\n")

            # Добавление содержимого из файла --append-text-file
            if args.append_text_file:
                append_file_abs_path = os.path.abspath(args.append_text_file)
                outfile.write(
                    "\n\n\n# ==============================================================================\n")
                outfile.write(f"# ДОПОЛНИТЕЛЬНЫЙ ТЕКСТ ИЗ ФАЙЛА: {os.path.basename(append_file_abs_path)}\n")
                outfile.write("# ==============================================================================\n\n")
                if os.path.exists(append_file_abs_path):
                    try:
                        with open(append_file_abs_path, 'r',
                                  encoding='utf-8') as appendfile:  # Предполагаем utf-8 для этого файла
                            outfile.write(appendfile.read())
                        print(f"INFO: Добавлено содержимое из файла: {append_file_abs_path}")
                    except Exception as e:
                        error_msg_append = f"# ОШИБКА: Не удалось прочитать файл для добавления '{append_file_abs_path}': {e}\n"
                        outfile.write(error_msg_append)
                        print(f"ERROR: {error_msg_append.strip()}")
                else:
                    error_msg_notfound = f"# Файл для добавления '{append_file_abs_path}' не найден.\n"
                    outfile.write(error_msg_notfound)
                    print(f"WARNING: {error_msg_notfound.strip()}")

        print(f"\nINFO: Обработка завершена. Результат сохранен в: {output_file_abs_path}")

    except Exception as e:
        print(f"КРИТИЧЕСКАЯ ОШИБКА: Произошла непредвиденная ошибка во время выполнения скрипта: {e}")
        import traceback
        traceback.print_exc()


if __name__ == "__main__":
    main()
