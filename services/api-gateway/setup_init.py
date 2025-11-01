# setup_init.py (run une fois, commit dans Git)
import os

packages = [
    'src',
    'src/api',
    'src/api/models',
    'src/database',
    'src/repositories',
    'src/services',
    'src/routes'
]

for package in packages:
    os.makedirs(package, exist_ok=True)
    init_file = os.path.join(package, '__init__.py')
    if not os.path.exists(init_file):
        open(init_file, 'a').close()
        print(f'✅ {init_file}')