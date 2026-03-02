import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: 'export',  // 输出纯静态文件到 out 目录
  trailingSlash: true,
  images: {
    unoptimized: true,  // GitHub Pages 不支持图片优化
  },
};

export default nextConfig;
