import Modal from './Modal'
import Button from './Button'

export interface ErrorModalProps {
  isOpen: boolean
  title?: string
  message: string
  onClose: () => void
}

export default function ErrorModal({ isOpen, title = '번역을 완료하지 못했어요', message, onClose }: ErrorModalProps) {
  return (
    <Modal isOpen={isOpen} onClose={onClose} title={title} size="sm">
      <p className="whitespace-pre-wrap text-sm text-gray-700 dark:text-gray-300">{message}</p>
      <div className="mt-4 flex justify-end">
        <Button onClick={onClose}>확인</Button>
      </div>
    </Modal>
  )
}
