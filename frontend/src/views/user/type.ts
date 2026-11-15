export interface ModeItem {
  title: string
  icon: string
  subtitle: string
  value?: string
  type: 'password'
  jumpMode?: 'link' | 'modal'
  status: boolean
  statusString: string
}
