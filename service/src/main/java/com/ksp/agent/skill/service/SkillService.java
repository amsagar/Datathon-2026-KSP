package com.ksp.agent.skill.service;

import com.ksp.agent.audit.config.dto.RevisionDto;
import com.ksp.agent.skill.dto.request.UpdateSkillRequest;
import com.ksp.agent.skill.dto.response.SkillDto;
import com.ksp.agent.skill.dto.response.SkillFileContentDto;
import com.ksp.agent.skill.dto.response.SkillFileNodeDto;
import com.ksp.agent.skill.entity.AgentSkill;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface SkillService {

    List<SkillDto> list(String assistantId);

    SkillDto get(String id);

    SkillDto create(String assistantId, MultipartFile file);

    SkillDto update(String id, UpdateSkillRequest request, MultipartFile file);

    void delete(String id);

    List<SkillFileNodeDto> listFiles(String id);

    SkillFileContentDto getFileContent(String id, String path);

    SkillDto updateFileContent(String id, String path, String content);

    /** Zip archive of all files in the skill bundle. */
    byte[] downloadBundle(String id);

    /** Enabled skills for an assistant, used to materialize a runtime workspace. */
    List<AgentSkill> forAssistant(String assistantId);

    /**
     * Reverts a skill to a prior archived {@code config_revision}: copies the archived blob prefix
     * ({@code revision.contentRef()}) into a fresh live prefix, patches name/description/enabled
     * from the snapshot, and records a new revision (revert always produces a fresh one, since the
     * file content itself changed).
     */
    SkillDto revertToVersion(String id, RevisionDto revision);
}
