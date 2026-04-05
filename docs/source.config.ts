import { defineDocs, defineConfig } from 'fumadocs-mdx/config';
import { rehypeNimbus } from './lib/rehype-nimbus';

export const docs = defineDocs({
  dir: 'content/docs',
});

export default defineConfig({
  mdxOptions: {
    // rehypePlugins listed here run AFTER Fumadocs' built-in rehypeCode
    rehypePlugins: [rehypeNimbus],
  },
});
