package com.ksp.agent.tool.imports;

import com.ksp.agent.tool.imports.dto.ImportRequest;
import com.ksp.agent.tool.imports.dto.ImportResult;

public interface ToolImportService {

    ImportResult importByKind(String kind, String assistantId, ImportRequest request);
}
