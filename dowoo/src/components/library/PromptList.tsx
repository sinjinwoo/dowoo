import type { Prompt } from '../../types/prompt'
import Button from '../ui/Button'

export interface PromptListProps {
  prompts: Prompt[]
  onCreate: () => void
  onEdit: (prompt: Prompt) => void
  onDelete: (prompt: Prompt) => void
}

export default function PromptList({ prompts, onCreate, onEdit, onDelete }: PromptListProps) {
  return (
    <div className="space-y-3">
      <Button className="w-full" onClick={onCreate}>
        + 새 프롬프트
      </Button>

      {prompts.length === 0 ? (
        <p className="py-8 text-center text-sm text-gray-400">등록된 프롬프트가 없습니다.</p>
      ) : (
        <div className="space-y-2">
          {prompts.map((prompt) => (
            <div
              key={prompt.id}
              className="flex items-center gap-2 rounded-lg border border-gray-200 bg-white p-3 dark:border-gray-800 dark:bg-gray-900"
            >
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold" title={prompt.title}>
                  {prompt.title}
                  {prompt.isDefault && (
                    <span className="ml-2 rounded bg-accent/10 px-1.5 py-0.5 text-[10px] font-normal text-accent">
                      기본
                    </span>
                  )}
                </p>
              </div>
              <Button variant="ghost" size="sm" onClick={() => onEdit(prompt)}>
                편집
              </Button>
              {!prompt.isDefault && (
                <Button variant="ghost" size="sm" onClick={() => onDelete(prompt)}>
                  삭제
                </Button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
