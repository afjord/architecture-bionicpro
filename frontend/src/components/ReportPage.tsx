import React, { useEffect, useState } from 'react';

const apiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8000';

type Report = {
  reportPeriodStart: string;
  reportPeriodEnd: string;
  processedUntil: string;
  fullName: string;
  email: string;
  prosthesisModel: string;
  prosthesisSerial: string;
  telemetryEvents: number;
  totalSteps: number;
  totalGripCycles: number;
  avgBatteryLevel: number;
  minBatteryLevel: number;
  maxLoadKg: number;
  errorEvents: number;
  generatedAt: string;
};

type ReportResponse = {
  status: string;
  source: 'olap' | 's3';
  reportUrl: string;
  objectKey: string;
  periodStart: string;
  periodEnd: string;
  report?: Report;
};

const ReportPage: React.FC = () => {
  const [authenticated, setAuthenticated] = useState(false);
  const [initialized, setInitialized] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<Report | null>(null);
  const [reportResponse, setReportResponse] = useState<ReportResponse | null>(null);

  useEffect(() => {
    const loadSession = async () => {
      try {
        const response = await fetch(`${apiUrl}/auth/session`, {
          credentials: 'include'
        });
        setAuthenticated(response.ok);
      } catch {
        setAuthenticated(false);
      } finally {
        setInitialized(true);
      }
    };

    loadSession();
  }, []);

  const login = () => {
    window.location.href = `${apiUrl}/auth/login`;
  };

  const logout = async () => {
    await fetch(`${apiUrl}/auth/logout`, {
      method: 'POST',
      credentials: 'include'
    });
    setAuthenticated(false);
  };

  const loadReport = async () => {
    try {
      setLoading(true);
      setError(null);
      setReport(null);
      setReportResponse(null);

      const response = await fetch(`${apiUrl}/reports`, {
        credentials: 'include'
      });

      if (response.status === 401) {
        setAuthenticated(false);
        setError('Not authenticated');
        return;
      }

      const body = await response.json();

      if (response.status === 404 && body.status === 'not_ready') {
        setError(body.message || 'Report has not been prepared yet');
        return;
      }

      if (!response.ok) {
        throw new Error(body.message || 'Report request failed');
      }

      if (!body.reportUrl) {
        throw new Error('Report response does not contain CDN URL');
      }

      setReportResponse(body);
      setReport(body.report || null);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
    }
  };

  if (!initialized) {
    return <div>Loading...</div>;
  }

  if (!authenticated) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
        <button
          onClick={login}
          className="px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600"
        >
          Login
        </button>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-100 px-4 py-10">
      <div className="mx-auto max-w-3xl rounded-lg bg-white p-6 shadow-md">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <h1 className="text-2xl font-bold">Usage Reports</h1>

          <button
            onClick={logout}
            className="rounded bg-gray-500 px-4 py-2 text-white hover:bg-gray-600"
          >
            Logout
          </button>
        </div>
        
        <button
          onClick={loadReport}
          disabled={loading}
          className={`mt-6 rounded bg-blue-500 px-4 py-2 text-white hover:bg-blue-600 ${
            loading ? 'opacity-50 cursor-not-allowed' : ''
          }`}
        >
          {loading ? 'Loading Report...' : 'Get My Report'}
        </button>

        {error && (
          <div className="mt-4 rounded bg-red-100 p-4 text-red-700">
            {error}
          </div>
        )}

        {reportResponse && (
          <div className="mt-4 rounded border border-blue-200 bg-blue-50 p-4 text-blue-900">
            <div className="font-semibold">Report is ready</div>
            <div className="mt-1 text-sm">
              Source: {reportResponse.source === 's3' ? 'S3 cache' : 'OLAP, then saved to S3'}
            </div>
            <a
              href={reportResponse.reportUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-2 inline-block text-blue-700 underline"
            >
              Open report via CDN
            </a>
          </div>
        )}

        {report && (
          <div className="mt-6 space-y-5">
            <div>
              <h2 className="text-xl font-semibold">{report.fullName}</h2>
              <p className="text-sm text-gray-600">{report.email}</p>
              <p className="mt-1 text-sm text-gray-600">
                Period: {report.reportPeriodStart} - {report.reportPeriodEnd}
              </p>
              <p className="text-sm text-gray-600">
                Processed until: {new Date(report.processedUntil).toLocaleString()}
              </p>
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <Metric label="Prosthesis model" value={report.prosthesisModel} />
              <Metric label="Serial number" value={report.prosthesisSerial} />
              <Metric label="Telemetry events" value={report.telemetryEvents} />
              <Metric label="Total steps" value={report.totalSteps} />
              <Metric label="Grip cycles" value={report.totalGripCycles} />
              <Metric label="Average battery" value={`${report.avgBatteryLevel}%`} />
              <Metric label="Minimum battery" value={`${report.minBatteryLevel}%`} />
              <Metric label="Max load" value={`${report.maxLoadKg} kg`} />
              <Metric label="Error events" value={report.errorEvents} />
              <Metric label="Generated at" value={new Date(report.generatedAt).toLocaleString()} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

const Metric: React.FC<{ label: string; value: string | number }> = ({ label, value }) => (
  <div className="rounded border border-gray-200 p-3">
    <div className="text-sm text-gray-500">{label}</div>
    <div className="mt-1 font-semibold text-gray-900">{value}</div>
  </div>
);

export default ReportPage;
