import React, { useEffect, useMemo, useState } from 'react';
import { motion } from 'motion/react';
import { MapPin } from 'lucide-react';
import dayjs, { Dayjs } from 'dayjs';
import { analyticsApi, HotspotRow, LookupRow } from '@apiCalls/analytics';
import HotspotMap from '@src/uiTemplates/HotspotMap';
import CustomRangePicker from '@atoms/CustomRangePicker';
import CustomSelect from '@atoms/CustomSelect';
import { Card } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import { useT } from '@constants/translations';
import { useLangStore } from '@store/useLangStore';
import { districtLabel } from '@utils/districtLabel';
import * as styles from '@styles/analyticsLayout.module.scss';

const DEFAULT_RANGE: [Dayjs, Dayjs] = [dayjs('2025-01-01'), dayjs('2026-06-30')];

const CrimeMapView: React.FC = () => {
  const t = useT();
  const lang = useLangStore((s) => s.lang);
  const [range, setRange] = useState<[Dayjs, Dayjs]>(DEFAULT_RANGE);
  const [districtId, setDistrictId] = useState<number | undefined>(1);
  const [crimeHeadId, setCrimeHeadId] = useState<number | undefined>();
  const [districts, setDistricts] = useState<LookupRow[]>([]);
  const [crimeHeads, setCrimeHeads] = useState<LookupRow[]>([]);
  const [points, setPoints] = useState<HotspotRow[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    analyticsApi.districts().then(setDistricts).catch(() => undefined);
    analyticsApi.crimeHeads().then(setCrimeHeads).catch(() => undefined);
  }, []);

  useEffect(() => {
    setLoading(true);
    analyticsApi
      .hotspots({
        from: range[0].format('YYYY-MM-DD'),
        to: range[1].format('YYYY-MM-DD'),
        districtId,
        crimeHeadId,
      })
      .then(setPoints)
      .catch(() => undefined)
      .finally(() => setLoading(false));
  }, [range, districtId, crimeHeadId]);

  const totalCases = useMemo(
    () => points.reduce((s, p) => s + Number(p.weight), 0),
    [points]
  );

  return (
    <>
      <div className={styles.toolbar}>
        <div className={styles.toolbarLead}>
          <span className={styles.toolbarMeta}>
            {loading && !points.length
              ? 'Loading…'
              : `${totalCases.toLocaleString()} cases · ${points.length.toLocaleString()} spots`}
          </span>
        </div>
        <div className={styles.toolbarFilters}>
          <CustomRangePicker
            className="w-auto min-w-[240px]"
            value={range}
            onChange={(v) => v && v[0] && v[1] && setRange([v[0], v[1]])}
            allowClear={false}
          />
          <CustomSelect<number>
            className="min-w-[180px]"
            fullWidth={false}
            allowClear
            placeholder={t('allDistricts')}
            value={districtId}
            onChange={setDistrictId}
            options={districts
              .filter((d) => d.district_id != null)
              .map((d) => ({
                value: d.district_id as number,
                label: districtLabel(d, lang),
              }))}
          />
          <CustomSelect<number>
            className="min-w-[180px]"
            fullWidth={false}
            allowClear
            placeholder={t('allCrimeHeads')}
            value={crimeHeadId}
            onChange={setCrimeHeadId}
            options={crimeHeads
              .filter((c) => c.crime_head_id != null)
              .map((c) => ({ value: c.crime_head_id as number, label: c.crime_group_name }))}
          />
        </div>
      </div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] }}
        className={styles.mapShell}
      >
        <Card className="flex h-full min-h-0 flex-1 flex-col gap-0 overflow-hidden py-0">
          <div className="flex flex-shrink-0 items-center gap-2 border-b border-border px-4 py-3">
            <MapPin className="size-4 text-primary" aria-hidden />
            <span className="text-sm font-semibold text-foreground">Incident hotspots</span>
            <span className="ml-auto text-xs font-medium text-muted-foreground tabular-nums">
              {loading && !points.length
                ? 'Loading…'
                : `${totalCases.toLocaleString()} cases · ${points.length.toLocaleString()} spots`}
            </span>
          </div>
          {loading && !points.length ? (
            <div className="flex-1 p-4">
              <Skeleton className="h-full min-h-[240px] w-full rounded-lg" />
            </div>
          ) : (
            <div className={`${styles.vizStage} ${styles.vizBleed}`}>
              <HotspotMap
                key={`${districtId ?? 'all'}-${crimeHeadId ?? 'all'}-${range[0].format('YYYY-MM-DD')}-${range[1].format('YYYY-MM-DD')}`}
                fitKey={`${districtId ?? 'all'}-${crimeHeadId ?? 'all'}-${points.length}`}
                data={{
                  points: points.map((p) => ({
                    lat: Number(p.lat),
                    lng: Number(p.lng),
                    weight: Number(p.weight),
                    label: p.crime_head,
                  })),
                }}
                fillHeight
              />
              <div className={styles.mapLegend}>
                <span>
                  <i style={{ background: '#c9962b' }} /> Low density
                </span>
                <span>
                  <i style={{ background: '#e67e22' }} /> Medium
                </span>
                <span>
                  <i style={{ background: '#c0392b' }} /> High density
                </span>
                <span className={styles.mapLegendHint}>zoom in for top spots</span>
              </div>
            </div>
          )}
        </Card>
      </motion.div>
    </>
  );
};

export default CrimeMapView;
