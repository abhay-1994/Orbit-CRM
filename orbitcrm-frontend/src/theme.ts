import type { ThemeConfig } from 'antd'

/** Brand colours from the OrbitCRM logo. */
export const brand = {
  navy: '#032577',
  purple: '#bd38fa',
  cyan: '#1edcfc',
} as const

export const theme: ThemeConfig = {
  token: {
    colorPrimary: brand.purple,
    colorInfo: brand.navy,
    colorLink: brand.purple,
    colorTextHeading: brand.navy,
    colorBgLayout: '#f4f6fb',
    borderRadius: 8,
  },
  components: {
    Table: { headerBg: '#f6f3fd', headerColor: brand.navy },
    Menu: { itemSelectedBg: '#f6e9fe', itemSelectedColor: brand.navy },
  },
}
