import { Routes, Route } from 'react-router-dom'

function App() {
  return (
    <Routes>
      <Route path="/" element={<div className="p-8 text-2xl font-bold">DVCS Platform</div>} />
    </Routes>
  )
}

export default App
