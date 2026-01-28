import { useState } from 'react';
import axios from 'axios';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

interface DataPoint {
  timestamp: number;
  value: number;
}

interface ChatAnalysisResult {
  streamId: string;
  dataPoints: DataPoint[];
}

function App() {
  const [streamId, setStreamId] = useState('');
  const [chatData, setChatData] = useState<ChatAnalysisResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchChatAnalysis = async () => {
    if (!streamId) {
      setError('스트림 ID를 입력해주세요.');
      setChatData(null);
      return;
    }

    setLoading(true);
    setError(null);
    setChatData(null);

    try {
      const response = await axios.get<ChatAnalysisResult>(`/api/v1/analysis/${streamId}`);
      setChatData(response.data);
    } catch (err) {
      if (axios.isAxiosError(err) && err.response) {
        if (err.response.status === 404) {
          setError('해당 스트림 ID의 분석 데이터를 찾을 수 없습니다.');
        } else {
          setError(`API 호출 중 오류 발생: ${err.response.status} ${err.response.statusText}`);
        }
      } else {
        setError('알 수 없는 오류가 발생했습니다.');
      }
      console.error(err);
    }
    setLoading(false);
  };

  return (
    <div className="min-h-screen bg-chzzk_bg text-chzzk_text p-8">
      <header className="text-center mb-12">
        <h1 className="text-5xl font-bold text-chzzk_purple mb-4">치지직 채팅 화력 분석기</h1>
        <p className="text-chzzk_light_gray text-lg">실시간 채팅 데이터를 시각화하고 하이라이트를 찾아보세요.</p>
      </header>

      <div className="max-w-4xl mx-auto bg-chzzk_dark rounded-lg shadow-xl p-8 mb-8">
        <div className="flex flex-col sm:flex-row gap-4 mb-6">
          <input
            type="text"
            className="flex-grow p-3 rounded-md bg-chzzk_bg border border-chzzk_purple focus:outline-none focus:ring-2 focus:ring-chzzk_purple text-chzzk_text"
            placeholder="스트림 ID를 입력하세요 (예: abcde1234)"
            value={streamId}
            onChange={(e) => setStreamId(e.target.value)}
          />
          <button
            className="px-6 py-3 rounded-md bg-chzzk_purple text-white font-semibold hover:bg-purple-700 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
            onClick={fetchChatAnalysis}
            disabled={loading}
          >
            {loading ? '조회 중...' : '조회하기'}
          </button>
        </div>

        {error && <p className="text-red-500 text-center mb-4">오류: {error}</p>}

        {chatData && chatData.dataPoints.length > 0 && (
          <div className="mt-8">
            <h2 className="text-2xl font-bold text-chzzk_text mb-4 text-center">'${chatData.streamId}' 스트림 채팅 화력</h2>
            <div className="bg-chzzk_bg p-4 rounded-lg" style={{ width: '100%', height: 400 }}>
              <ResponsiveContainer width="100%" height="100%">
                <LineChart
                  data={chatData.dataPoints.map(dp => ({ ...dp, timestamp: new Date(dp.timestamp).toLocaleString() }))}
                  margin={{
                    top: 5,
                    right: 30,
                    left: 20,
                    bottom: 5,
                  }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#444" />
                  <XAxis dataKey="timestamp" stroke="#e0e0e0" />
                  <YAxis stroke="#e0e0e0" />
                  <Tooltip contentStyle={{ backgroundColor: '#333', borderColor: '#555' }} itemStyle={{ color: '#e0e0e0' }}/>
                  <Line type="monotone" dataKey="value" stroke="#8a2be2" activeDot={{ r: 8 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        )}

        {chatData && chatData.dataPoints.length === 0 && !loading && !error && (
          <p className="text-chzzk_light_gray text-center mt-8">해당 스트림 ID의 분석 데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}

export default App;