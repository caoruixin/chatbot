interface LayoutProps {
  title: string;
  children: React.ReactNode;
}

function Layout({ title, children }: LayoutProps) {
  return (
    <div className="flex h-screen flex-col bg-gray-50">
      <header className="flex h-14 shrink-0 items-center border-b border-gray-200 bg-white px-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-800">{title}</h1>
      </header>
      <main className="flex min-h-0 flex-1">{children}</main>
    </div>
  );
}

export default Layout;
