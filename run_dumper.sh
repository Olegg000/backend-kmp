#!/bin/bash

# Путь к твоему скрипту project_dumper.py
# Если run_dumper.sh лежит в той же папке, что и project_dumper.py:
PYTHON_SCRIPT="./project_dumper.py"
# Если project_dumper.py лежит в другом месте, укажи полный или относительный путь:
# PYTHON_SCRIPT="/путь/к/твоим/скриптам/project_dumper.py"

# Директория проекта, которую нужно обработать
PROJECT_ROOT="./" # Или абсолютный путь /полный/путь/к/dataGetter

# Файл с дополнительным текстом
APPEND_TEXT_FILE="" # Убедись, что этот файл существует или путь верный

# Имя выходного файла (без пути, только имя)
OUTPUT_FILENAME="RESULT.txt"

# Директория для сохранения дампа
OUTPUT_DIR="./" # Или другое место

# Кодировка файлов проекта и обработка ошибок (если нужно переопределить дефолтные)
# ENCODING_ARGS="--encoding utf-8 --error-handling surrogateescape"
ENCODING_ARGS="" # Пусто, если устраивают дефолты

# Файл игнорирования (если не стандартный .project_copier_ignore в корне проекта)
# IGNORE_FILE_ARG="--ignore-file ./dataGetter/.custom_ignore"
IGNORE_FILE_ARG="" # Пусто, если используется стандартный


# Запуск скрипта python
echo "Запуск создания дампа проекта..."
python3 "$PYTHON_SCRIPT" "$PROJECT_ROOT" \
    --append-text-file "$APPEND_TEXT_FILE" \
    --output-filename "$OUTPUT_FILENAME" \
    --output-dir "$OUTPUT_DIR" \
    $ENCODING_ARGS \
    $IGNORE_FILE_ARG

# Проверка кода завершения (опционально)
if [ $? -eq 0 ]; then
    echo "Дамп успешно создан!"
else
    echo "Во время создания дампа произошла ошибка."
fi
