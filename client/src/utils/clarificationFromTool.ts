import type {
  ClarificationQuestionDto,
  ToolCallDto,
  UiToolCall,
} from '@interfaces/chat.interface';

export interface PersistedClarification {
  questions: ClarificationQuestionDto[];
  answers: Record<string, string>;
}

const ASK_USER_TOOL = 'AskUserQuestionTool';
export const ASK_USER_TOOL_LABEL = 'Ask user';

export const isAskUserQuestionTool = (name: string) =>
  name === ASK_USER_TOOL || name === ASK_USER_TOOL_LABEL;

/** Parse questions/answers from a persisted AskUserQuestionTool row. */
export const clarificationFromTool = (
  tool: ToolCallDto | UiToolCall
): PersistedClarification | null => {
  if (!isAskUserQuestionTool(tool.name)) return null;
  const questions = parseQuestions(tool.input);
  const answers = parseAnswers(tool.output);
  if (!questions.length || !Object.keys(answers).length) return null;
  return { questions, answers };
};

export const questionsInputJson = (
  questions: ClarificationQuestionDto[]
): string => JSON.stringify({ questions }, null, 2);

export const answersOutputJson = (answers: Record<string, string>): string =>
  JSON.stringify({ answers }, null, 2);

const formatQuestionBlock = (q: ClarificationQuestionDto): string => {
  const header = q.header?.trim() ? `**${q.header.trim()}**\n` : '';
  const options =
    q.options.length > 0
      ? `\n${q.options.map((o) => `- ${o.label}`).join('\n')}`
      : '';
  return `${header}${q.question}${options}`;
};

/** Human-readable markdown for tool card output. */
export const formatClarificationMarkdown = (
  questions: ClarificationQuestionDto[],
  answers: Record<string, string>
): string =>
  questions
    .map((q) => {
      const block = formatQuestionBlock(q);
      const ans = answers[q.question]?.trim();
      return ans ? `${block}\n\n→ ${ans}` : block;
    })
    .join('\n\n');

/** Keep AskUserQuestion in the tool list with readable payloads for ToolEventCard. */
export const enrichAskUserTool = (tool: UiToolCall): UiToolCall => {
  if (!isAskUserQuestionTool(tool.name)) return tool;
  const persisted = clarificationFromTool(tool);
  const displayName = ASK_USER_TOOL_LABEL;
  if (persisted) {
    return {
      ...tool,
      name: displayName,
      input: persisted.questions.map(formatQuestionBlock).join('\n\n'),
      output: formatClarificationMarkdown(
        persisted.questions,
        persisted.answers
      ),
      running: false,
    };
  }
  const questions = parseQuestions(tool.input);
  if (questions.length) {
    return {
      ...tool,
      name: displayName,
      input: questions.map(formatQuestionBlock).join('\n\n'),
      running: tool.running,
    };
  }
  return { ...tool, name: displayName };
};

const parseQuestions = (input: string | null): ClarificationQuestionDto[] => {
  if (!input?.trim()) return [];
  try {
    const parsed = JSON.parse(input) as {
      questions?: Array<{
        question?: string;
        header?: string;
        multiSelect?: boolean;
        options?: Array<{ label?: string; description?: string }>;
      }>;
    };
    if (!Array.isArray(parsed.questions)) return [];
    return parsed.questions
      .filter((q) => q?.question)
      .map((q) => ({
        question: q.question!,
        header: q.header || '',
        multiSelect: Boolean(q.multiSelect),
        options: (q.options || []).map((o) => ({
          label: o.label || '',
          description: o.description || '',
        })),
      }));
  } catch {
    return [];
  }
};

const parseAnswers = (output: string | null): Record<string, string> => {
  if (!output?.trim()) return {};
  try {
    const parsed = JSON.parse(output) as { answers?: Record<string, string> };
    if (parsed.answers && typeof parsed.answers === 'object') {
      return parsed.answers;
    }
  } catch {
    // formatted markdown from older rows — ignore
  }
  return {};
};
