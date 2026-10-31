package com.xiaoying.materialstudio.task;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface TaskImageGenerator {
    void generate(List<Path> inputImages, Path output) throws IOException;
}
