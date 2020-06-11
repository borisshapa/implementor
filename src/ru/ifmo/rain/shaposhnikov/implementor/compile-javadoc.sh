#!/bin/bash

root=../../../../../../../
java_advanced_2020=${root}java-advanced-2020
java_advanced_2020_solutions=${root}java-advanced-2020-solutions
java_solutions=${java_advanced_2020_solutions}/java-solutions
module_name=ru.ifmo.rain.shaposhnikov.implementor
implementor_path=ru/ifmo/rain/shaposhnikov/implementor
my_modules=${java_advanced_2020_solutions}/_build/modules

path_to_files=${java_advanced_2020}/modules/info.kgeorgiy.java.advanced.implementor/info/kgeorgiy/java/advanced/implementor
module_path=${my_modules}/${module_name}/${implementor_path}
mkdir -p ${module_path}

cp -r ${java_solutions}/${implementor_path}/*.java ${module_path}
cp ${java_solutions}/module-info.java ${my_modules}/${module_name}

javadoc -d _javadoc -link https://docs.oracle.com/en/java/javase/11/docs/api \
    --module-path ${java_advanced_2020}/lib:${java_advanced_2020}/artifacts -private -version -author \
    --module-source-path ${my_modules}:${java_advanced_2020}/modules \
    --module ${module_name} \
    ${path_to_files}/Impler.java \
    ${path_to_files}/ImplerException.java \
    ${path_to_files}/JarImpler.java

