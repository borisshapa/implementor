#!/bin/bash

root=../../../../../../../
java_advanced_2020=${root}java-advanced-2020
java_advanced_2020_solutions=${root}java-advanced-2020-solutions
java_solutions=${java_advanced_2020_solutions}/java-solutions
module_name=ru.ifmo.rain.shaposhnikov.implementor
implementor_path=ru/ifmo/rain/shaposhnikov/implementor
build=${java_advanced_2020_solutions}/_build
my_modules=${build}/modules
compiled_classes=${build}/compiled_classes

module_path=${my_modules}/${module_name}/${implementor_path}
mkdir -p ${module_path}

cp -r ${java_solutions}/${implementor_path}/*.java ${module_path}
cp ${java_solutions}/module-info.java ${my_modules}/${module_name}

javac -d ${compiled_classes} \
          --module-path ${java_advanced_2020}/lib:${java_advanced_2020}/artifacts \
          --module-source-path ${my_modules}:${java_advanced_2020}/modules \
          --module ${module_name}

cd ${compiled_classes}/${module_name} || exit

jar -c --file=../_implementor.jar \
    --main-class=ru.ifmo.rain.shaposhnikov.implementor.JarImplementor \
    --module-path=../../../../java_advanced_2020/lib:../../../../java_advanced_2020/artifacts \
     module-info.class \
    ru/ifmo/rain/shaposhnikov/implementor/*.class

cd ../info.kgeorgiy.java.advanced.implementor || exit

jar uf ../_implementor.jar info/kgeorgiy/java/advanced/implementor/*.class

cd ../../..
cp _build/compiled_classes/_implementor.jar java-solutions/${implementor_path}
rm _build/compiled_classes/_implementor.jar