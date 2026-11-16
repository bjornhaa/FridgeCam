import { Navigate, Route, Routes } from 'react-router'
import { InnloggingProvider } from './innlogging/Innlogging.tsx'
import { KreverInnlogging } from './innlogging/KreverInnlogging.tsx'
import { LoggInnSide } from './sider/LoggInnSide.tsx'
import { RegistrerSide } from './sider/RegistrerSide.tsx'
import { SkannSide } from './sider/SkannSide.tsx'

export default function App() {
  return (
    <InnloggingProvider>
      <Routes>
        <Route path="/logg-inn" element={<LoggInnSide />} />
        <Route path="/registrer" element={<RegistrerSide />} />
        <Route element={<KreverInnlogging />}>
          <Route path="/" element={<SkannSide />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </InnloggingProvider>
  )
}
