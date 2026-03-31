
script_dir=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
base=$(echo $script_dir | sed -r 's/(savant-dependency-management).*/\1/')
echo $base

mkdir -p $base/build/license-list-data
git clone --depth=1 git@github.com:spdx/license-list-data.git $base/build/license-list-data
rm -rf $base/src/main/resources/license-details
rm -rf $base/src/main/resources/license-exceptions
rm $base/src/main/resources/exceptions.json
rm $base/src/main/resources/licenses.json
cp -Rp $base/build/license-list-data/json/details $base/src/main/resources/license-details
cp -Rp $base/build/license-list-data/json/exceptions $base/src/main/resources/license-exceptions
cp -p $base/build/license-list-data/json/exceptions.json $base/src/main/resources/exceptions.json
cp -p $base/build/license-list-data/json/licenses.json $base/src/main/resources/licenses.json


