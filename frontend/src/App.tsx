import { Navigate, Route, Routes } from 'react-router'
import { InnloggingProvider } from './innlogging/Innlogging.tsx'
import { KreverInnlogging } from './innlogging/KreverInnlogging.tsx'
import { Faner } from './navigasjon/Faner.tsx'
import { KjoleskapSide } from './sider/KjoleskapSide.tsx'
import { LoggInnSide } from './sider/LoggInnSide.tsx'
import { ProfilSide } from './sider/ProfilSide.tsx'
import { RegistrerSide } from './sider/RegistrerSide.tsx'
import { SkannSide } from './sider/SkannSide.tsx'

export default function App() {
  return (
    <InnloggingProvider>
      <Routes>
        <Route path="/logg-inn" element={<LoggInnSide />} />
        <Route path="/registrer" element={<RegistrerSide />} />
        <Route element={<KreverInnlogging />}>
          <Route element={<Faner />}>
            <Route path="/" element={<SkannSide />} />
            <Route path="/kjoleskap" element={<KjoleskapSide />} />
            <Route path="/profil" element={<ProfilSide />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </InnloggingProvider>
  )
}
