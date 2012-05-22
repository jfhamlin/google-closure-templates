ant SoyToPySrcCompiler && \
java -jar ../build/SoyToPySrcCompiler.jar --shouldGeneratePydoc \
     --compileTimeGlobalsFile "../python/test_files/globals_file" \
     --outputPathFormat "../python/features.py" ../examples/features.soy &&
java -jar ../build/SoyToPySrcCompiler.jar --shouldGeneratePydoc \
     --compileTimeGlobalsFile "../python/test_files/globals_file" \
     --outputPathFormat "../python/simple.py" ../examples/simple.soy

