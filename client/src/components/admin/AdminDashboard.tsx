import React, { useState, useEffect } from 'react';

interface MetricsOverview {
  systemStatus: string;
  targetChannelsCount: number;
  activeAnalyzingCount: number;
  liveStreamsCount: number;
  todayHighlightCount: number;
  hikariActiveConnections: number;
  hikariPendingConnections: number;
  hikariIdleConnections: number;
  kafkaLag: number;
}

interface ExcludedChannel {
  channelId: string;
  streamerName: string;
  reason: string;
  expiresAt: string | null;
  updatedAt: string;
}

interface SystemConfig {
  configKey: string;
  configValue: string;
  description: string;
  category: string;
  updatedAt: string;
}

export const AdminDashboard = () => {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('admin_token'));
  const [password, setPassword] = useState('');
  const [authError, setAuthError] = useState('');
  const [activeTab, setActiveTab] = useState<'metrics' | 'blacklist' | 'configs'>('metrics');

  // 모니터링 상태
  const [metrics, setMetrics] = useState<MetricsOverview | null>(null);
  const [loadingMetrics, setLoadingMetrics] = useState(false);

  // 블랙리스트 상태
  const [excludedList, setExcludedList] = useState<ExcludedChannel[]>([]);
  const [newChannelId, setNewChannelId] = useState('');
  const [newStreamerName, setNewStreamerName] = useState('');
  const [newReason, setNewReason] = useState('');
  const [durationDays, setDurationDays] = useState<number>(30);
  const [blacklistMsg, setBlacklistMsg] = useState('');

  // 설정 상태
  const [configs, setConfigs] = useState<SystemConfig[]>([]);
  const [editingValues, setEditingValues] = useState<{ [key: string]: string }>({});
  const [configMsg, setConfigMsg] = useState('');

  const getHeaders = () => ({
    'Content-Type': 'application/json',
    'X-Admin-Token': token || '',
  });

  // 1. 로그인
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    try {
      const res = await fetch('/api/v1/admin/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password }),
      });
      if (!res.ok) {
        throw new Error('비밀번호가 일치하지 않습니다.');
      }
      const data = await res.json();
      localStorage.setItem('admin_token', data.token);
      setToken(data.token);
      setPassword('');
    } catch (err: any) {
      setAuthError(err.message || '로그인 실패');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('admin_token');
    setToken(null);
  };

  // 2. 모니터링 조회
  const fetchMetrics = async () => {
    if (!token) return;
    setLoadingMetrics(true);
    try {
      const res = await fetch('/api/v1/admin/metrics/overview', { headers: getHeaders() });
      if (res.status === 401) {
        handleLogout();
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setMetrics(data);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingMetrics(false);
    }
  };

  // 3. 블랙리스트 조회
  const fetchExcluded = async () => {
    if (!token) return;
    try {
      const res = await fetch('/api/v1/admin/targets/excluded', { headers: getHeaders() });
      if (res.status === 401) {
        handleLogout();
        return;
      }
      if (res.ok) {
        const data = await res.json();
        setExcludedList(data);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 4. 블랙리스트 등록
  const handleExcludeChannel = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newChannelId.trim()) return;
    try {
      const res = await fetch('/api/v1/admin/targets/exclude', {
        method: 'POST',
        headers: getHeaders(),
        body: JSON.stringify({
          channelId: newChannelId.trim(),
          streamerName: newStreamerName.trim() || newChannelId.trim(),
          reason: newReason.trim() || '관리자 수동 제외',
          durationDays: durationDays > 0 ? durationDays : null,
        }),
      });
      if (res.ok) {
        setBlacklistMsg('채널이 성공적으로 격리되었습니다.');
        setNewChannelId('');
        setNewStreamerName('');
        setNewReason('');
        fetchExcluded();
        setTimeout(() => setBlacklistMsg(''), 3000);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 5. 블랙리스트 복원
  const handleRestore = async (channelId: string) => {
    if (!confirm(`${channelId} 채널의 격리를 해제하시겠습니까?`)) return;
    try {
      const res = await fetch(`/api/v1/admin/targets/exclude/${channelId}`, {
        method: 'DELETE',
        headers: getHeaders(),
      });
      if (res.ok) {
        fetchExcluded();
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 6. 캐시 강제 무효화
  const handleRefreshCache = async () => {
    try {
      const res = await fetch('/api/v1/admin/targets/cache-refresh', {
        method: 'POST',
        headers: getHeaders(),
      });
      if (res.ok) {
        setBlacklistMsg('타겟 캐시가 즉시 동기화되었습니다.');
        setTimeout(() => setBlacklistMsg(''), 3000);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 7. 시스템 설정 조회
  const fetchConfigs = async () => {
    if (!token) return;
    try {
      const res = await fetch('/api/v1/admin/configs', { headers: getHeaders() });
      if (res.status === 401) {
        handleLogout();
        return;
      }
      if (res.ok) {
        const data: SystemConfig[] = await res.json();
        setConfigs(data);
        const map: { [key: string]: string } = {};
        data.forEach(c => (map[c.configKey] = c.configValue));
        setEditingValues(map);
      }
    } catch (err) {
      console.error(err);
    }
  };

  // 8. 시스템 설정 저장
  const handleSaveConfig = async (key: string) => {
    const val = editingValues[key];
    try {
      const res = await fetch(`/api/v1/admin/configs/${encodeURIComponent(key)}`, {
        method: 'PUT',
        headers: getHeaders(),
        body: JSON.stringify({ value: val }),
      });
      if (res.ok) {
        setConfigMsg(`'${key}' 설정이 저장되었습니다.`);
        fetchConfigs();
        setTimeout(() => setConfigMsg(''), 3000);
      }
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => {
    if (token) {
      if (activeTab === 'metrics') fetchMetrics();
      if (activeTab === 'blacklist') fetchExcluded();
      if (activeTab === 'configs') fetchConfigs();
    }
  }, [token, activeTab]);

  // 10초마다 모니터링 자동 갱신
  useEffect(() => {
    if (!token || activeTab !== 'metrics') return;
    const interval = setInterval(fetchMetrics, 10000);
    return () => clearInterval(interval);
  }, [token, activeTab]);

  // 로그인 화면
  if (!token) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh] px-4">
        <div className="w-full max-w-md p-8 bg-[#141416] border border-[#2A2A2C] rounded-2xl shadow-2xl">
          <div className="text-center mb-6">
            <span className="text-3xl">🔒</span>
            <h2 className="text-xl font-bold text-white mt-2">치즈픽 관리자 콘솔</h2>
            <p className="text-xs text-gray-400 mt-1">접근을 위해 마스터 키(ADMIN_SECRET_KEY)를 입력하세요.</p>
          </div>
          {authError && (
            <div className="mb-4 p-3 bg-rose-500/10 border border-rose-500/30 rounded-lg text-rose-400 text-xs text-center font-medium">
              {authError}
            </div>
          )}
          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="관리자 마스터 키 입력"
                className="w-full px-4 py-3 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl text-white placeholder-gray-500 text-sm focus:outline-none focus:border-[#00FFA3]"
                autoFocus
              />
            </div>
            <button
              type="submit"
              className="w-full py-3 bg-[#00FFA3] text-black font-bold text-sm rounded-xl hover:bg-[#00FFA3]/90 transition-all shadow-lg shadow-[#00FFA3]/10"
            >
              대시보드 잠금 해제
            </button>
          </form>
        </div>
      </div>
    );
  }

  // 관리자 대시보드 메인
  return (
    <div className="w-full space-y-6">
      {/* 헤더 & 탭 바 */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 pb-4 border-b border-[#2A2A2C]">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-2xl">⚡</span>
            <h1 className="text-2xl font-black text-white italic tracking-tight">CHEESE-PICK ADMIN</h1>
            <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-[#00FFA3]/20 text-[#00FFA3] border border-[#00FFA3]/30">
              OPERATOR
            </span>
          </div>
          <p className="text-xs text-gray-400 mt-0.5">시스템 헬스 모니터링, 이벤트 채널 격리 및 런타임 파라미터 관리</p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={handleLogout}
            className="px-3 py-1.5 text-xs text-gray-400 hover:text-white bg-[#1C1C1E] border border-[#2A2A2C] rounded-lg transition-all"
          >
            로그아웃
          </button>
        </div>
      </div>

      {/* 탭 네비게이션 */}
      <div className="flex gap-2 border-b border-[#2A2A2C] pb-2">
        <button
          onClick={() => setActiveTab('metrics')}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'metrics'
              ? 'bg-[#00FFA3] text-black shadow-lg shadow-[#00FFA3]/20'
              : 'text-gray-400 hover:text-white hover:bg-[#1C1C1E]'
          }`}
        >
          📊 핵심 모니터링
        </button>
        <button
          onClick={() => setActiveTab('blacklist')}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'blacklist'
              ? 'bg-[#00FFA3] text-black shadow-lg shadow-[#00FFA3]/20'
              : 'text-gray-400 hover:text-white hover:bg-[#1C1C1E]'
          }`}
        >
          🚫 채널 격리 관리 (블랙리스트)
        </button>
        <button
          onClick={() => setActiveTab('configs')}
          className={`px-4 py-2 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'configs'
              ? 'bg-[#00FFA3] text-black shadow-lg shadow-[#00FFA3]/20'
              : 'text-gray-400 hover:text-white hover:bg-[#1C1C1E]'
          }`}
        >
          ⚙️ 비즈니스 파라미터 조절
        </button>
      </div>

      {/* 탭 1: 핵심 모니터링 */}
      {activeTab === 'metrics' && (
        <div className="space-y-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="text-xs font-bold text-gray-400">종합 건강도:</span>
              <span
                className={`px-2.5 py-1 rounded-full text-xs font-black ${
                  metrics?.systemStatus === 'ALL_HEALTHY'
                    ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30'
                    : metrics?.systemStatus === 'WARNING'
                    ? 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                    : 'bg-rose-500/20 text-rose-400 border border-rose-500/30'
                }`}
              >
                ● {metrics?.systemStatus || 'CHECKING...'}
              </span>
            </div>
            <button
              onClick={fetchMetrics}
              disabled={loadingMetrics}
              className="px-3 py-1 bg-[#1C1C1E] border border-[#2A2A2C] rounded-lg text-xs text-gray-300 hover:text-[#00FFA3] transition-all"
            >
              {loadingMetrics ? '갱신 중...' : '🔄 지금 새로고침'}
            </button>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
              <span className="text-xs text-gray-400 font-bold uppercase">타겟 모니터링 풀</span>
              <div className="text-2xl font-black text-white mt-2">
                {metrics?.targetChannelsCount ?? 0}{' '}
                <span className="text-xs font-normal text-gray-400">/ 300</span>
              </div>
              <p className="text-[11px] text-gray-500 mt-1">Redis stream:targets</p>
            </div>

            <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
              <span className="text-xs text-gray-400 font-bold uppercase">실시간 화력 분석 중</span>
              <div className="text-2xl font-black text-[#00FFA3] mt-2">
                {metrics?.activeAnalyzingCount ?? 0}{' '}
                <span className="text-xs font-normal text-gray-400">개 채널</span>
              </div>
              <p className="text-[11px] text-gray-500 mt-1">현재 활성 라이브: {metrics?.liveStreamsCount ?? 0}개</p>
            </div>

            <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
              <span className="text-xs text-gray-400 font-bold uppercase">오늘 생성된 하이라이트</span>
              <div className="text-2xl font-black text-amber-400 mt-2">
                {metrics?.todayHighlightCount ?? 0}{' '}
                <span className="text-xs font-normal text-gray-400">건</span>
              </div>
              <p className="text-[11px] text-gray-500 mt-1">00:00 KST 이후 누적</p>
            </div>

            <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
              <span className="text-xs text-gray-400 font-bold uppercase">DB 커넥션 (HikariCP)</span>
              <div className="text-2xl font-black text-white mt-2">
                {metrics?.hikariActiveConnections ?? 0}{' '}
                <span className="text-xs font-normal text-gray-400">
                  / Active (대기 {metrics?.hikariPendingConnections ?? 0})
                </span>
              </div>
              <p className="text-[11px] text-gray-500 mt-1">Idle: {metrics?.hikariIdleConnections ?? 0}개</p>
            </div>
          </div>
        </div>
      )}

      {/* 탭 2: 채널 격리 관리 (블랙리스트) */}
      {activeTab === 'blacklist' && (
        <div className="space-y-6">
          {blacklistMsg && (
            <div className="p-3 bg-[#00FFA3]/10 border border-[#00FFA3]/30 rounded-xl text-[#00FFA3] text-xs font-bold text-center">
              {blacklistMsg}
            </div>
          )}

          {/* 등록 폼 */}
          <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
            <h3 className="text-sm font-bold text-white mb-3">🚫 단기 이벤트/중계 채널 격리 (블랙리스트) 등록</h3>
            <p className="text-xs text-gray-400 mb-4">
              등록된 채널은 <strong>엔진 수집 타겟</strong>과 <strong>메인 리더보드 순위</strong>에서 즉시 제외됩니다. (사용자 직접 검색은 유지)
            </p>
            <form onSubmit={handleExcludeChannel} className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
              <input
                type="text"
                placeholder="채널 ID (예: 7c142828b058c1...)"
                value={newChannelId}
                onChange={(e) => setNewChannelId(e.target.value)}
                className="px-3 py-2 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00FFA3]"
                required
              />
              <input
                type="text"
                placeholder="채널명 (예: 아시안게임 MBC)"
                value={newStreamerName}
                onChange={(e) => setNewStreamerName(e.target.value)}
                className="px-3 py-2 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00FFA3]"
              />
              <input
                type="text"
                placeholder="사유 (예: 대회 종료)"
                value={newReason}
                onChange={(e) => setNewReason(e.target.value)}
                className="px-3 py-2 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl text-xs text-white placeholder-gray-500 focus:outline-none focus:border-[#00FFA3]"
              />
              <div className="flex gap-2">
                <select
                  value={durationDays}
                  onChange={(e) => setDurationDays(Number(e.target.value))}
                  className="px-3 py-2 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl text-xs text-white focus:outline-none focus:border-[#00FFA3] grow"
                >
                  <option value={30}>30일 후 자동 해제 (추천)</option>
                  <option value={7}>7일 후 자동 해제</option>
                  <option value={14}>14일 후 자동 해제</option>
                  <option value={0}>영구 제외</option>
                </select>
                <button
                  type="submit"
                  className="px-4 py-2 bg-rose-500 text-white font-bold text-xs rounded-xl hover:bg-rose-600 transition-all whitespace-nowrap shadow-md shadow-rose-500/20"
                >
                  격리 등록
                </button>
              </div>
            </form>
          </div>

          {/* 격리 목록 */}
          <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold text-white">
                현재 격리된 채널 목록 ({excludedList.length}개)
              </h3>
              <button
                onClick={handleRefreshCache}
                className="px-3 py-1 bg-[#1C1C1E] border border-[#2A2A2C] rounded-lg text-xs text-gray-300 hover:text-[#00FFA3] transition-all"
              >
                ⚡ 타겟 캐시 즉시 비우기
              </button>
            </div>

            {excludedList.length === 0 ? (
              <div className="text-center py-8 text-xs text-gray-500">현재 격리된 채널이 없습니다.</div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead>
                    <tr className="border-b border-[#2A2A2C] text-gray-400 font-bold">
                      <th className="py-2.5 px-3">스트리머명</th>
                      <th className="py-2.5 px-3">채널 ID</th>
                      <th className="py-2.5 px-3">제외 사유</th>
                      <th className="py-2.5 px-3">만료 일시</th>
                      <th className="py-2.5 px-3 text-right">동작</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#2A2A2C]/50">
                    {excludedList.map((item) => (
                      <tr key={item.channelId} className="hover:bg-[#1C1C1E]/50">
                        <td className="py-3 px-3 font-bold text-white">{item.streamerName}</td>
                        <td className="py-3 px-3 font-mono text-gray-400 text-[11px]">{item.channelId}</td>
                        <td className="py-3 px-3 text-gray-300">{item.reason || '-'}</td>
                        <td className="py-3 px-3 text-gray-400">
                          {item.expiresAt ? new Date(item.expiresAt).toLocaleString() : '영구'}
                        </td>
                        <td className="py-3 px-3 text-right">
                          <button
                            onClick={() => handleRestore(item.channelId)}
                            className="px-2.5 py-1 bg-[#1C1C1E] border border-[#2A2A2C] hover:border-emerald-500/50 hover:text-emerald-400 text-gray-300 rounded-lg text-[11px] transition-all"
                          >
                            격리 해제
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        </div>
      )}

      {/* 탭 3: 비즈니스 파라미터 조절 */}
      {activeTab === 'configs' && (
        <div className="space-y-6">
          {configMsg && (
            <div className="p-3 bg-[#00FFA3]/10 border border-[#00FFA3]/30 rounded-xl text-[#00FFA3] text-xs font-bold text-center">
              {configMsg}
            </div>
          )}

          <div className="p-5 bg-[#141416] border border-[#2A2A2C] rounded-2xl">
            <h3 className="text-sm font-bold text-white mb-2">⚙️ 런타임 비즈니스 파라미터 (Dynamic Config)</h3>
            <p className="text-xs text-gray-400 mb-6">
              서버 재배포 없이 분석 윈도우, 하이라이트 영상 버퍼, 쿨다운 등을 즉시 변경합니다.
            </p>

            <div className="space-y-4">
              {configs.map((config) => (
                <div
                  key={config.configKey}
                  className="p-4 bg-[#1C1C1E] border border-[#2A2A2C] rounded-xl flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4"
                >
                  <div className="grow">
                    <div className="flex items-center gap-2">
                      <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-white/10 text-gray-300">
                        {config.category}
                      </span>
                      <span className="font-mono text-xs font-bold text-white">{config.configKey}</span>
                    </div>
                    <p className="text-xs text-gray-400 mt-1">{config.description}</p>
                  </div>
                  <div className="flex items-center gap-2 w-full sm:w-auto">
                    <input
                      type="text"
                      value={editingValues[config.configKey] ?? config.configValue}
                      onChange={(e) =>
                        setEditingValues({ ...editingValues, [config.configKey]: e.target.value })
                      }
                      className="px-3 py-1.5 bg-[#141416] border border-[#2A2A2C] rounded-lg text-xs font-mono text-white focus:outline-none focus:border-[#00FFA3] w-24 sm:w-28 text-center"
                    />
                    <button
                      onClick={() => handleSaveConfig(config.configKey)}
                      className="px-3 py-1.5 bg-[#00FFA3] text-black font-bold text-xs rounded-lg hover:bg-[#00FFA3]/90 transition-all whitespace-nowrap shadow-md shadow-[#00FFA3]/10"
                    >
                      저장
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
