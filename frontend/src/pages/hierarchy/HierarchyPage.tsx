import { Box, Button, ButtonBase, TextField, Typography } from '@mui/material'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import { useMemo, useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { PageHeader } from '../../components/common/PageHeader'
import { ErrorCard, LoadingCard } from '../../components/common/PageState'
import { RoleChip } from '../../components/common/RoleChip'
import { Facts, Section } from '../../components/common/Section'
import { StatusChip } from '../../components/common/StatusChip'
import { useApiData } from '../../hooks/useApiData'
import { api } from '../../services/api'
import { colors } from '../../theme/tokens'
import type { HierarchyNode } from '../../types/domain'
import { money } from '../../utils/format'
import { ROLE_LABELS } from '../../utils/roles'

function flatten(nodes: HierarchyNode[], depth = 0, out: Array<{ node: HierarchyNode; depth: number }> = []) {
  for (const node of nodes) {
    out.push({ node, depth })
    flatten(node.children, depth + 1, out)
  }
  return out
}

function countBelow(node: HierarchyNode): number {
  return node.children.reduce((sum, c) => sum + 1 + countBelow(c), 0)
}

export function HierarchyPage() {
  const tree = useApiData(() => api.get<HierarchyNode[]>('/hierarchy/tree'), [])
  const [open, setOpen] = useState<Set<number>>(new Set())
  const [selected, setSelected] = useState<number | null>(null)
  const [q, setQ] = useState('')

  const all = useMemo(() => flatten(tree.data ?? []), [tree.data])
  const parentOf = useMemo(() => {
    const map = new Map<number, number>()
    const walk = (nodes: HierarchyNode[], parent?: number) => nodes.forEach((n) => {
      if (parent !== undefined) map.set(n.id, parent)
      walk(n.children, n.id)
    })
    walk(tree.data ?? [])
    return map
  }, [tree.data])

  const expanded = useMemo(() => {
    if (tree.data && open.size === 0 && !q) return new Set(tree.data.map((n) => n.id))
    return open
  }, [open, tree.data, q])

  const visible = all.filter(({ node }) => {
    if (q) {
      const needle = q.toLowerCase()
      return node.accountCode.toLowerCase().includes(needle) || node.displayName.toLowerCase().includes(needle)
    }
    let p = parentOf.get(node.id)
    while (p !== undefined) {
      if (!expanded.has(p)) return false
      p = parentOf.get(p)
    }
    return true
  })
  const sel = all.find((r) => r.node.id === selected)?.node ?? all[0]?.node

  function toggle(id: number) {
    const next = new Set(expanded)
    if (next.has(id)) next.delete(id)
    else next.add(id)
    setOpen(next)
  }

  return (
    <>
      <PageHeader eyebrow="Network" title="Hierarchy explorer" subtitle="Your downstream network. You only see accounts below you."
        actions={<Button variant="outlined" color="inherit" onClick={() => setOpen(new Set(all.map((r) => r.node.id)))}>Expand all</Button>} />
      {tree.error && <ErrorCard message={tree.error} onRetry={tree.reload} />}
      {tree.loading && !tree.data && <LoadingCard />}
      {tree.data && (
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'flex-start' }}>
          <Section sx={{ flex: '2 1 520px', minWidth: 0 }}>
            <TextField size="small" label="Find in network" placeholder="Name or user ID" value={q} onChange={(e) => setQ(e.target.value)} />
            {visible.length === 0 && <Typography color="text.secondary" variant="body2">No accounts {q ? 'match' : 'below you yet'}.</Typography>}
            <Box component="ul" role="tree" aria-label="Network" sx={{ listStyle: 'none', m: 0, p: 0, display: 'flex', flexDirection: 'column', gap: 0.25 }}>
              {visible.map(({ node, depth }) => {
                const hasKids = node.children.length > 0
                const isOpen = expanded.has(node.id)
                return (
                  <Box component="li" role="treeitem" aria-expanded={hasKids ? isOpen : undefined} aria-selected={sel?.id === node.id} key={node.id}>
                    <ButtonBase
                      onClick={() => {
                        setSelected(node.id)
                        if (hasKids && !q) toggle(node.id)
                      }}
                      sx={{ width: '100%', minHeight: 48, justifyContent: 'flex-start', gap: 1.25, pl: 1.5 + (q ? 0 : depth * 3.5), pr: 1.5, borderRadius: 2.5, bgcolor: sel?.id === node.id ? colors.indigo50 : 'transparent', textAlign: 'left' }}
                    >
                      <Box sx={{ width: 20, color: 'text.secondary', display: 'flex' }}>{hasKids && !q ? (isOpen ? <ExpandMoreRounded fontSize="small" /> : <ChevronRightRounded fontSize="small" />) : null}</Box>
                      <RoleChip role={node.role} />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Typography sx={{ fontWeight: 600, fontSize: 14 }} noWrap>{node.displayName}</Typography>
                        <Typography sx={{ fontSize: 12, color: 'text.secondary' }}>{node.accountCode} · {countBelow(node)} below</Typography>
                      </Box>
                      {node.status !== 'ACTIVE' && <StatusChip status={node.status} />}
                    </ButtonBase>
                  </Box>
                )
              })}
            </Box>
          </Section>
          {sel && (
            <Section title={sel.displayName} sx={{ flex: '1 1 300px', minWidth: 0 }}>
              <Facts items={[
                ['User ID', sel.accountCode], ['Role', ROLE_LABELS[sel.role]], ['Business', sel.businessName], ['Status', <StatusChip key="s" status={sel.status} />],
                ['Wallet available', money(sel.walletBalance)], ['Direct children', String(sel.children.length)], ['Accounts below', String(countBelow(sel))],
              ]} />
              <Button variant="outlined" color="inherit" component={RouterLink} to={`/users/${sel.id}`}>View details</Button>
            </Section>
          )}
        </Box>
      )}
    </>
  )
}
