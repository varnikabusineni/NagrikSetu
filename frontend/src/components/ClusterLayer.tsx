import L from 'leaflet';
import 'leaflet.markercluster';
import { useEffect } from 'react';
import { useMap } from 'react-leaflet';

export interface ClusterPoint {
  id: number;
  lat: number;
  lng: number;
  colour: string;
  popupHtml: string;
}

/** Renders many markers efficiently with leaflet.markercluster. */
export function ClusterLayer({ points, onOpen }: { points: ClusterPoint[]; onOpen: (id: number) => void }) {
  const map = useMap();
  useEffect(() => {
    const group = L.markerClusterGroup({
      showCoverageOnHover: false,
      maxClusterRadius: 50,
      iconCreateFunction: (cluster) => {
        const n = cluster.getChildCount();
        const size = n < 10 ? 34 : n < 100 ? 40 : 48;
        return L.divIcon({
          html: `<div class="marker-cluster-custom" style="width:${size}px;height:${size}px">${n}</div>`,
          className: '',
          iconSize: [size, size],
        });
      },
    });
    points.forEach((p) => {
      const marker = L.marker([p.lat, p.lng], {
        icon: L.divIcon({
          className: '',
          html: `<div class="marker-pin" style="width:18px;height:18px;background:${p.colour}"></div>`,
          iconSize: [18, 18],
          iconAnchor: [9, 9],
          popupAnchor: [0, -9],
        }),
        keyboard: true,
      });
      const container = document.createElement('div');
      container.innerHTML = p.popupHtml;
      container.querySelector('[data-open]')?.addEventListener('click', () => onOpen(p.id));
      marker.bindPopup(container);
      group.addLayer(marker);
    });
    map.addLayer(group);
    return () => {
      map.removeLayer(group);
    };
  }, [map, points, onOpen]);
  return null;
}
