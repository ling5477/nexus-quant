"""完整链正负例使用逐步持久化形态；缺任一关联不能增加已完成数。"""
import copy
import unittest
from l6_storage_counter import count


def fixture():
    order = dict(order_id='o', client_order_id='c', external_order_id='v', status='FILLED',
                 qty='0.1', trade_env='SIM', account_id=1, strategy_run_id='r', symbol='BTC-USDT')
    trade = dict(trade_id='t', order_id='o', external_order_id='v', exchange_trade_id='b0-fill-v',
                 qty='0.1', price='100', fee='0.01', trade_env='SIM', account_id=1, symbol='BTC-USDT', fee_currency='USDT', client_order_id='c')
    entries = [dict(entry_id=i, idempotency_key=str(i), ref_id='t', account_id=1, currency='USDT',
                    delta=d, balance_after=b) for i, (d, b) in enumerate([('-10','-10'),('-0.01','-0.01'),('0.01','0'),('10','0')])]
    return {'facts': {'orders':[order], 'trades':[trade], 'events':[{'event_id':'e','payload':dict(trade)}],
                     'ledger_entries':entries, 'ledger_events':[{'entry_id':i} for i in range(4)],
                     'positions':[{'account_id':1,'qty':'0.1','symbol':'BTC-USDT','avg_price':'100','available_qty':'0.1','frozen_qty':'0'}],
                     'account_snapshots':[{'snapshot_id':1,'account_id':1,'currency':'BTC','balance':'0.1','available':'0.1','frozen':'0'},
                                          {'snapshot_id':2,'account_id':1,'currency':'USDT','balance':'0','available':'0','frozen':'0'}],
                     'strategy_runs':[{'strategy_run_id':'r','status':'SUCCEEDED'}],
                     'strategy_run_dispatch_work':[{'strategy_run_id':'r','client_order_id':'c','effective_quantity':'0.1'}],
                     'ordinary_place_authorities':[{'order_id':'o','state':'MAY_HAVE_ESCAPED'}]},
            'venue':{'events':[{'type':'REQUEST_RECEIVED','client':'c'}],
                     'data':[{'clOrdId':'c','state':'filled','ordId':'v','accFillSz':'0.1'}]}}


class CounterTest(unittest.TestCase):
    def test_order_trade_event_and_accounting_cuts(self):
        for absent in [('trades','events','ledger_entries','ledger_events','positions','account_snapshots'),
                       ('events','ledger_entries','ledger_events','positions','account_snapshots'),
                       ('ledger_entries','ledger_events','positions','account_snapshots'),
                       ('positions',), ('account_snapshots',), ('ledger_events',)]:
            p=fixture()
            for key in absent:p['facts'][key]=[]
            self.assertEqual(0,count(p)['fullChainCompleted'])

    def test_complete_chain_repeated_read_is_not_incremental(self):
        p=fixture()
        self.assertEqual(1,count(p)['fullChainCompleted'])
        self.assertEqual(count(p),count(copy.deepcopy(p)))
        self.assertEqual(['o'],count(p)['fullChainOrderIds'])

    def test_venue_and_projection_lineage_are_required(self):
        for key in ('data','events'):
            p=fixture();p['venue'][key]=[]
            if key=='data':self.assertEqual(0,count(p)['fullChainCompleted'])
            else:
                with self.assertRaises(ValueError):count(p)
        p=fixture();p['facts']['positions'][0]['qty']='0.2'
        self.assertEqual(0,count(p)['fullChainCompleted'])

    def test_wrong_account_environment_and_symbol_never_complete(self):
        for key, value in [('account_id',999),('trade_env','LIVE'),('symbol','ETH-USDT')]:
            p=fixture();p['facts']['events'][0]['payload'][key]=value
            with self.assertRaises(ValueError):count(p)
        p=fixture();p['facts']['positions'][0]['symbol']='ETH-USDT'
        self.assertEqual(0,count(p)['fullChainCompleted'])

    def test_replay_duplicate_facts_reject(self):
        for key in ('trades','events','ledger_entries'):
            p=fixture();p['facts'][key].append(copy.deepcopy(p['facts'][key][0]))
            with self.assertRaises(ValueError):count(p)


if __name__=='__main__':unittest.main()
