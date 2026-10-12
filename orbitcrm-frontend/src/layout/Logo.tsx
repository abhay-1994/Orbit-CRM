import logoUrl from '../assets/logo.png'

/**
 * OrbitCRM logo and wordmark. The mark has a transparent background, so it works on the white header
 * and on the dark sign-in panel (`light` switches the wordmark to white).
 */
export function Logo({ size = 40, light = false }: { size?: number; light?: boolean }) {
  return (
    <span className="logo">
      <img src={logoUrl} alt="" height={size} />
      <span className="logo-word" style={light ? { color: '#fff' } : undefined}>
        Orbit<span>CRM</span>
      </span>
    </span>
  )
}
