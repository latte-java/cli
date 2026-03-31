#!/bin/bash
files=$(find test-deps | grep '\.amd' | grep -v '\.md5')

for f in ${files}; do
  echo ${f}
  md5sum ${f} > ${f}.md5
done