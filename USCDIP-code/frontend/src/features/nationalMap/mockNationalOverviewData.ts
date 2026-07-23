import type {
  CityNodeData,
  FlyLineConfig,
  NationalSummary,
  ProvinceHealthData,
  RiskLevel
} from './chinaMapTypes';

interface ProvinceSeed {
  adcode: string;
  provinceName: string;
  base: number;
  riskLevel: RiskLevel;
}

const PROVINCE_SEEDS: ProvinceSeed[] = [
  { adcode: '110000', provinceName: '北京市', base: 92, riskLevel: 'LOW' },
  { adcode: '120000', provinceName: '天津市', base: 86, riskLevel: 'MEDIUM' },
  { adcode: '130000', provinceName: '河北省', base: 78, riskLevel: 'HIGH' },
  { adcode: '140000', provinceName: '山西省', base: 76, riskLevel: 'HIGH' },
  { adcode: '150000', provinceName: '内蒙古自治区', base: 84, riskLevel: 'MEDIUM' },
  { adcode: '210000', provinceName: '辽宁省', base: 79, riskLevel: 'HIGH' },
  { adcode: '220000', provinceName: '吉林省', base: 87, riskLevel: 'MEDIUM' },
  { adcode: '230000', provinceName: '黑龙江省', base: 83, riskLevel: 'MEDIUM' },
  { adcode: '310000', provinceName: '上海市', base: 94, riskLevel: 'LOW' },
  { adcode: '320000', provinceName: '江苏省', base: 91, riskLevel: 'LOW' },
  { adcode: '330000', provinceName: '浙江省', base: 93, riskLevel: 'LOW' },
  { adcode: '340000', provinceName: '安徽省', base: 85, riskLevel: 'MEDIUM' },
  { adcode: '350000', provinceName: '福建省', base: 89, riskLevel: 'LOW' },
  { adcode: '360000', provinceName: '江西省', base: 84, riskLevel: 'MEDIUM' },
  { adcode: '370000', provinceName: '山东省', base: 82, riskLevel: 'MEDIUM' },
  { adcode: '410000', provinceName: '河南省', base: 74, riskLevel: 'CRITICAL' },
  { adcode: '420000', provinceName: '湖北省', base: 81, riskLevel: 'HIGH' },
  { adcode: '430000', provinceName: '湖南省', base: 86, riskLevel: 'MEDIUM' },
  { adcode: '440000', provinceName: '广东省', base: 90, riskLevel: 'LOW' },
  { adcode: '450000', provinceName: '广西壮族自治区', base: 83, riskLevel: 'MEDIUM' },
  { adcode: '460000', provinceName: '海南省', base: 88, riskLevel: 'LOW' },
  { adcode: '500000', provinceName: '重庆市', base: 77, riskLevel: 'HIGH' },
  { adcode: '510000', provinceName: '四川省', base: 80, riskLevel: 'HIGH' },
  { adcode: '520000', provinceName: '贵州省', base: 82, riskLevel: 'MEDIUM' },
  { adcode: '530000', provinceName: '云南省', base: 84, riskLevel: 'MEDIUM' },
  { adcode: '540000', provinceName: '西藏自治区', base: 91, riskLevel: 'LOW' },
  { adcode: '610000', provinceName: '陕西省', base: 79, riskLevel: 'HIGH' },
  { adcode: '620000', provinceName: '甘肃省', base: 81, riskLevel: 'HIGH' },
  { adcode: '630000', provinceName: '青海省', base: 90, riskLevel: 'LOW' },
  { adcode: '640000', provinceName: '宁夏回族自治区', base: 87, riskLevel: 'MEDIUM' },
  { adcode: '650000', provinceName: '新疆维吾尔自治区', base: 85, riskLevel: 'MEDIUM' },
  { adcode: '710000', provinceName: '台湾省', base: 88, riskLevel: 'LOW' },
  { adcode: '810000', provinceName: '香港特别行政区', base: 92, riskLevel: 'LOW' },
  { adcode: '820000', provinceName: '澳门特别行政区', base: 93, riskLevel: 'LOW' }
];

export const MOCK_NATIONAL_SUMMARY: NationalSummary = {
  healthScore: 85.7,
  coveredProvinceCount: 34,
  pipelineLengthKm: 187420,
  onlineDeviceCount: 12684,
  activeAlertCount: 38,
  openIncidentCount: 9,
  activeWorkOrderCount: 17,
  updatedAt: '2026-07-14T08:00:00+08:00'
};

export const MOCK_PROVINCE_HEALTH_DATA: ProvinceHealthData[] = PROVINCE_SEEDS.map(
  (seed, index) => ({
    adcode: seed.adcode,
    provinceName: seed.provinceName,
    healthScore: seed.base,
    pipelineLengthKm: 1850 + ((index * 947) % 8200),
    onlineDeviceCount: 120 + ((index * 173) % 760),
    activeAlertCount: seed.riskLevel === 'CRITICAL' ? 8 : seed.riskLevel === 'HIGH' ? 5 : index % 3,
    openIncidentCount: seed.riskLevel === 'CRITICAL' ? 3 : seed.riskLevel === 'HIGH' ? 2 : index % 2,
    riskLevel: seed.riskLevel
  })
);

export const MOCK_CITY_NODES: CityNodeData[] = [
  { id: 'beijing', cityName: '北京', coordinate: [116.4074, 39.9042], deviceCount: 682, alertCount: 1, healthScore: 92, riskLevel: 'LOW' },
  { id: 'shanghai', cityName: '上海', coordinate: [121.4737, 31.2304], deviceCount: 741, alertCount: 0, healthScore: 94, riskLevel: 'LOW' },
  { id: 'guangzhou', cityName: '广州', coordinate: [113.2644, 23.1291], deviceCount: 618, alertCount: 2, healthScore: 90, riskLevel: 'LOW' },
  { id: 'chengdu', cityName: '成都', coordinate: [104.0665, 30.5728], deviceCount: 533, alertCount: 5, healthScore: 80, riskLevel: 'HIGH' },
  { id: 'wuhan', cityName: '武汉', coordinate: [114.3054, 30.5931], deviceCount: 586, alertCount: 4, healthScore: 81, riskLevel: 'HIGH' },
  { id: 'xian', cityName: '西安', coordinate: [108.9398, 34.3416], deviceCount: 437, alertCount: 5, healthScore: 79, riskLevel: 'HIGH' },
  { id: 'shenyang', cityName: '沈阳', coordinate: [123.4315, 41.8057], deviceCount: 398, alertCount: 4, healthScore: 79, riskLevel: 'HIGH' },
  { id: 'urumqi', cityName: '乌鲁木齐', coordinate: [87.6168, 43.8256], deviceCount: 284, alertCount: 2, healthScore: 85, riskLevel: 'MEDIUM' }
];

const WUHAN: [number, number] = [114.3054, 30.5931];

export const MOCK_FLY_LINES: FlyLineConfig[] = [
  { id: 'wuhan-beijing', from: WUHAN, to: [116.4074, 39.9042], fromName: '武汉', toName: '北京', riskLevel: 'MEDIUM', volume: 76 },
  { id: 'wuhan-shanghai', from: WUHAN, to: [121.4737, 31.2304], fromName: '武汉', toName: '上海', riskLevel: 'LOW', volume: 92 },
  { id: 'wuhan-guangzhou', from: WUHAN, to: [113.2644, 23.1291], fromName: '武汉', toName: '广州', riskLevel: 'MEDIUM', volume: 68 },
  { id: 'wuhan-chengdu', from: WUHAN, to: [104.0665, 30.5728], fromName: '武汉', toName: '成都', riskLevel: 'HIGH', volume: 54 },
  { id: 'wuhan-xian', from: WUHAN, to: [108.9398, 34.3416], fromName: '武汉', toName: '西安', riskLevel: 'HIGH', volume: 49 },
  { id: 'wuhan-shenyang', from: WUHAN, to: [123.4315, 41.8057], fromName: '武汉', toName: '沈阳', riskLevel: 'MEDIUM', volume: 43 },
  { id: 'wuhan-urumqi', from: WUHAN, to: [87.6168, 43.8256], fromName: '武汉', toName: '乌鲁木齐', riskLevel: 'MEDIUM', volume: 37 }
];

