import React, { useEffect, useMemo, useRef, useState } from 'react';
import L from 'leaflet';
import {
  CircleMarker,
  MapContainer,
  Tooltip as LeafletTooltip,
  useMap,
  useMapEvents,
} from 'react-leaflet';
import 'leaflet.heat';
import 'leaflet/dist/leaflet.css';
import '@styles/leafletFixes.css';
import type { TemplateComponentProps } from './types';

interface HotspotPoint {
  lat?: number;
  lng?: number;
  weight?: number;
  label?: string;
}
interface HotspotData {
  title?: string;
  points?: HotspotPoint[];
}

type HotspotMapProps = TemplateComponentProps & {
  fillHeight?: boolean;
  /** Bump when filters change so the map re-fits even if point count is similar. */
  fitKey?: string | number;
};

type HeatLatLng = [number, number, number];

declare module 'leaflet' {
  function heatLayer(
    latlngs: HeatLatLng[],
    options?: {
      minOpacity?: number;
      maxZoom?: number;
      radius?: number;
      blur?: number;
      max?: number;
      gradient?: Record<number, string>;
    },
  ): L.Layer;
}

/** Keeps Leaflet in sync when the flex container actually gets a real size. */
const InvalidateSize: React.FC = () => {
  const map = useMap();
  useEffect(() => {
    const el = map.getContainer().parentElement;
    if (!el) return;
    const sync = () => map.invalidateSize({ pan: false });
    sync();
    const ro = new ResizeObserver(sync);
    ro.observe(el);
    window.addEventListener('resize', sync);
    return () => {
      ro.disconnect();
      window.removeEventListener('resize', sync);
    };
  }, [map]);
  return null;
};

/** Zoom/pan the map to the current hotspot set (district filter, etc.). */
const FitToPoints: React.FC<{ points: HotspotPoint[]; fitKey?: string | number }> = ({
  points,
  fitKey,
}) => {
  const map = useMap();

  useEffect(() => {
    if (!points.length) return;

    const run = () => {
      map.invalidateSize({ pan: false });
      const latLngs = points.map((p) =>
        L.latLng(p.lat as number, p.lng as number),
      );

      if (latLngs.length === 1) {
        map.setView(latLngs[0], 13, { animate: true });
        return;
      }

      const bounds = L.latLngBounds(latLngs);
      if (!bounds.isValid()) return;

      const spanM = bounds.getNorthEast().distanceTo(bounds.getSouthWest());
      const maxZoom = spanM < 80_000 ? 13 : 11;

      map.fitBounds(bounds, {
        padding: [56, 56],
        maxZoom,
        animate: true,
      });
    };

    const t = window.setTimeout(run, 50);
    return () => window.clearTimeout(t);
  }, [map, points, fitKey]);

  return null;
};

/**
 * OSM basemap tiles, slightly oversized so neighbouring tiles overlap and
 * hide the hairline seams Chrome/Safari leave between rasters.
 */
const SeamlessTileLayer: React.FC = () => {
  const map = useMap();

  useEffect(() => {
    const layer = L.tileLayer(
      'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
      {
        maxZoom: 19,
        detectRetina: false,
        attribution: '',
      },
    );

    type CreateTileShim = {
      createTile(coords: L.Coords, done?: L.DoneCallback): HTMLElement;
    };
    (layer as unknown as CreateTileShim).createTile = function createTile(
      this: L.TileLayer,
      coords: L.Coords,
      done?: L.DoneCallback,
    ) {
      const tile = (L.TileLayer.prototype as unknown as CreateTileShim).createTile.call(
        this,
        coords,
        done,
      ) as HTMLImageElement;
      tile.style.width = '257px';
      tile.style.height = '257px';
      tile.style.maxWidth = 'none';
      tile.style.maxHeight = 'none';
      tile.style.border = '0';
      tile.style.outline = '0';
      tile.style.padding = '0';
      tile.style.margin = '0';
      tile.style.boxShadow = 'none';
      tile.style.background = 'none';
      tile.style.mixBlendMode = 'normal';
      return tile;
    };

    layer.addTo(map);
    return () => {
      map.removeLayer(layer);
    };
  }, [map]);

  return null;
};

/** Soft density field — readable at city / state scale without overplotting. */
const HeatDensityLayer: React.FC<{ heat: HeatLatLng[]; maxIntensity: number }> = ({
  heat,
  maxIntensity,
}) => {
  const map = useMap();

  useEffect(() => {
    if (!heat.length) return;

    const layer = L.heatLayer(heat, {
      radius: 28,
      blur: 22,
      maxZoom: 16,
      minOpacity: 0.28,
      max: Math.max(maxIntensity, 0.35),
      gradient: {
        0.15: '#f0d48a',
        0.35: '#c9962b',
        0.55: '#e67e22',
        0.75: '#c0392b',
        1.0: '#6d1420',
      },
    });
    layer.addTo(map);
    return () => {
      map.removeLayer(layer);
    };
  }, [map, heat, maxIntensity]);

  return null;
};

/** Peek at the heaviest spots once zoomed in — keeps the overview clean. */
const TopSpotMarkers: React.FC<{
  points: HotspotPoint[];
  maxWeight: number;
}> = ({ points, maxWeight }) => {
  const map = useMap();
  const [zoom, setZoom] = useState(map.getZoom());

  useMapEvents({
    zoomend: () => setZoom(map.getZoom()),
  });

  const limit = zoom >= 14 ? 80 : 36;
  const top = useMemo(() => {
    return [...points]
      .sort((a, b) => (b.weight ?? 1) - (a.weight ?? 1))
      .slice(0, limit);
  }, [points, limit]);

  // Only reveal discrete spots when close enough to read them.
  if (zoom < 12) return null;

  return (
    <>
      {top.map((p, i) => {
        const w = p.weight ?? 1;
        const t = w / maxWeight;
        const fill =
          t > 0.66 ? '#c0392b' : t > 0.33 ? '#e67e22' : '#c9962b';
        return (
          <CircleMarker
            key={`${p.lat}-${p.lng}-${i}`}
            center={[p.lat as number, p.lng as number]}
            radius={4 + 7 * Math.sqrt(t)}
            pathOptions={{
              color: '#1a1510',
              fillColor: fill,
              fillOpacity: 0.85,
              weight: 1.25,
              opacity: 0.9,
            }}
          >
            <LeafletTooltip>
              {p.label ? `${p.label} — ` : ''}
              {w} case{w === 1 ? '' : 's'}
            </LeafletTooltip>
          </CircleMarker>
        );
      })}
    </>
  );
};

/** Incident/hotspot map — heatmap density + sparse top-spot markers on zoom-in. */
const HotspotMap: React.FC<HotspotMapProps> = ({ data, fillHeight, fitKey }) => {
  const d = (data ?? {}) as HotspotData;
  const wrapRef = useRef<HTMLDivElement>(null);

  const points = useMemo(
    () =>
      (Array.isArray(d.points) ? d.points : []).filter(
        (p) => typeof p.lat === 'number' && typeof p.lng === 'number',
      ),
    [d.points],
  );

  const maxWeight = useMemo(
    () => Math.max(...points.map((p) => p.weight ?? 1), 1),
    [points],
  );

  const heat = useMemo<HeatLatLng[]>(
    () =>
      points.map((p) => [
        p.lat as number,
        p.lng as number,
        // Soften extremes so a few huge cells don't wash the whole city red.
        Math.pow((p.weight ?? 1) / maxWeight, 0.65),
      ]),
    [points, maxWeight],
  );

  const maxIntensity = useMemo(
    () => Math.max(...heat.map((h) => h[2]), 0.35),
    [heat],
  );

  if (!points.length) {
    return fillHeight ? <div style={{ flex: 1, minHeight: 0 }} /> : null;
  }

  const centerLat =
    points.reduce((s, p) => s + (p.lat as number), 0) / points.length;
  const centerLng =
    points.reduce((s, p) => s + (p.lng as number), 0) / points.length;

  return (
    <div
      ref={wrapRef}
      className="hotspot-map-root"
      style={
        fillHeight
          ? { flex: 1, minHeight: 0, position: 'relative', width: '100%' }
          : { margin: '8px 0', height: 420, position: 'relative', width: '100%' }
      }
    >
      {d.title && (
        <div style={{ fontWeight: 650, marginBottom: 8, fontSize: 14 }}>
          {d.title}
        </div>
      )}
      <MapContainer
        center={[centerLat, centerLng]}
        zoom={12}
        attributionControl={false}
        zoomControl
        preferCanvas={false}
        style={
          fillHeight
            ? {
                position: 'absolute',
                inset: 0,
                height: '100%',
                width: '100%',
                borderRadius: 10,
                border: '1px solid rgba(176, 23, 34, 0.12)',
                background: '#abd2df',
              }
            : {
                height: '100%',
                width: '100%',
                borderRadius: 10,
                border: '1px solid rgba(176, 23, 34, 0.12)',
                background: '#abd2df',
              }
        }
        scrollWheelZoom
      >
        <InvalidateSize />
        <FitToPoints points={points} fitKey={fitKey} />
        <SeamlessTileLayer />
        <HeatDensityLayer heat={heat} maxIntensity={maxIntensity} />
        <TopSpotMarkers points={points} maxWeight={maxWeight} />
      </MapContainer>
    </div>
  );
};

export default HotspotMap;
