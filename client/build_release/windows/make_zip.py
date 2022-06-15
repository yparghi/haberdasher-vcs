#!/usr/bin/env python

import shutil
import sys

folder_to_zip = sys.argv[1]
out_path = sys.argv[2]

shutil.make_archive(out_path, 'zip', folder_to_zip)

