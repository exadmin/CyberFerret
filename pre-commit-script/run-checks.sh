#!/bin/bash
'''':
for interpreter in python3 python2 python
do
    which $interpreter >/dev/null 2>&1 && exec $interpreter "$0" "$@"
done
echo "$0: WARNING! No python is installed. Skip any checks" >&2
exit 0
# '''

import os
import sys

HERE = os.path.dirname(os.path.realpath(__file__))

def main():
    # cfg = os.path.join(HERE, 'orghooks.yaml')
    # cmd = ['pre-commit', 'run', '--config', cfg, '--files'] + sys.argv[1:]
    # os.execvp(cmd[0], cmd)
    print("Hello World!!!")

if __name__ == '__main__':
    exit(main())
