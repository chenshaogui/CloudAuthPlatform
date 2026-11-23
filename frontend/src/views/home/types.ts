export type WorkplaceTotal = {
  project: number;
  access: number;
  todo: number;
};

export type Notice = {
  title: string;
  type: string;
  date: Date | number | string;
};

export type Shortcut = {
  name: string;
  icon: string;
  url: string;
  color: string;
};
